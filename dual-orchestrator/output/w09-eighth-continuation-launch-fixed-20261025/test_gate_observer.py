"""Offline protocol fixtures, not real native capability or Gate authorization."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
import gate_support as g

class ObserverTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.ledger = self.root / '.agent/ws'
        self.sessions = self.root / 'sessions'
        self.sessions.mkdir()
        self.record = '44444444-4444-4444-8444-444444444444'
        self.meta = {'name':'reviewer', 'cwd':str(self.root), 'acp_session_id':'s'}
        g.write(self.sessions / (self.record + '.json'), self.meta)
    def fixed(self, d, r):
        output=d/'outputs';output.mkdir(exist_ok=True)
        hashes={o[k]:g.sha(self.root/o[k]) for o in r['agentResultAcquisition']['occurrences'] for k in ['transcriptPath','stderrPath']}
        g.write(output/'fixture-completion.json',dict(run_observations=r,output_hashes=hashes))
        g.write(d/'run.json',r)
    def refix_logs(self, d):
        self.fixed(d,g.read(d/'run.json'))
    def call(self, run, total, request='3', ordinal=1):
        d = self.ledger / 'runs' / run
        d.mkdir(parents=True, exist_ok=True)
        transcript, stderr = d / 'current.log', d / 'stderr.log'
        frame = {'id':request, 'result':{'stopReason':'end_turn', 'usage':{'inputTokens':total,'outputTokens':0,'totalTokens':total}}}
        transcript.write_text(json.dumps(frame)+'\n')
        stderr.write_text('[acpx] session reviewer ('+self.record+')')
        relative = lambda p: str(p.relative_to(self.root))
        usage = dict(inputTokens=total,outputTokens=0,cachedReadTokens=0,cachedWriteTokens=0,totalTokens=total,
                     measurementType='PROVIDER_RUN_USAGE',attribution='EXACT_REQUEST_MATCH',cumulative=False)
        o = dict(occurrence=ordinal,sessionName='reviewer',requestId=request,stopReason='end_turn',
                 transcriptPath=relative(transcript),stderrPath=relative(stderr),usage=usage,
                 processResult=dict(stdoutPath=relative(transcript),stderrPath=relative(stderr)))
        r = dict(runId=run,nodeId=run,agent='codex',finishedAt='2026-10-11T00:00:00Z',usage=usage,
                 agentResultAcquisition=dict(occurrences=[o]))
        self.fixed(d,r)
        return d, r, frame
    def measure(self):
        return g.measure(self.root,self.ledger,self.sessions)
    def test_readable_json_body_and_matching_completion_or_delayed_stream(self):
        d,r,frame=self.call('a',32)
        body='[thinking]\n[agent]\n{\n  "clauses": [\n    {"id":"AC-3","text":"body"}\n  ]\n}\n'
        (d/'current.log').write_text(body+json.dumps(frame)+'\n')
        self.refix_logs(d)
        self.assertEqual(32,self.measure()['actual_tokens'])
        (d/'current.log').write_text(body)
        self.refix_logs(d)
        request={'id':'3','method':'session/prompt','params':{'sessionId':'s'}}
        pair=json.dumps(request)+'\n'+json.dumps(frame)+'\n'
        stream=self.sessions/(self.record+'.stream.ndjson')
        stream.write_text(pair)
        self.assertEqual(32,self.measure()['actual_tokens'])
        stream.write_text(pair+pair)
        with self.assertRaises(AssertionError): self.measure()
    def test_parsed_invalid_rpc_frame_cannot_borrow_valid_completion(self):
        d,r,frame=self.call('a',32)
        for bad in [{'jsonrpc':'2.0','id':'3','result':42}, {'jsonrpc':'1.0','id':3,'result':{}}, {'jsonrpc':2.0,'id':3,'result':{}},
                    {'id':{},'result':{}}, {'id':3,'error':False},
                    {'id':3,'result':{},'error':{}}, {'jsonrpc':'2.0'}]:
            (d/'current.log').write_text(json.dumps(bad)+'\n'+json.dumps(frame)+'\n')
            self.refix_logs(d)
            with self.assertRaisesRegex(AssertionError,'Invalid current RPC frame'): self.measure()
    def test_plain_model_json_without_rpc_identity_is_not_a_frame(self):
        d,r,frame=self.call('a',32)
        (d/'current.log').write_text('{"result":{"text":"model body"}}\n'+json.dumps(frame)+'\n')
        self.refix_logs(d)
        self.assertEqual(32,self.measure()['actual_tokens'])
    def test_same_rpc_id_after_reinitialize_is_two_calls_and_history_append_stays_valid(self):
        _,_,a=self.call('a',28010)
        stream = self.sessions / (self.record+'.stream.ndjson')
        request={'id':'3','method':'session/prompt','params':{'sessionId':'s'}}
        stream.write_text(json.dumps(request)+'\n'+json.dumps(a)+'\n')
        first = self.measure()
        _,_,b=self.call('b',70687)
        stream.write_text(stream.read_text()+'{"id":0,"method":"initialize"}\n'+json.dumps(request)+'\n'+json.dumps(b)+'\n')
        value = self.measure()
        self.assertEqual((98697,2),(value['actual_tokens'],value['completed_calls']))
        self.assertEqual(first['completed_runs'][0],value['completed_runs'][0])
    def test_main_and_correction_count_as_two_owned_occurrences(self):
        d, first, _ = self.call('a',36945)
        _, second, _ = self.call('b',26548,ordinal=2)
        first['agentResultAcquisition']['occurrences'] += second['agentResultAcquisition']['occurrences']
        first['usage']=dict(first['usage'],inputTokens=63493,totalTokens=63493)
        self.fixed(d,first)
        (self.ledger/'runs/b/run.json').unlink()
        self.assertEqual(63493,self.measure()['actual_tokens'])
        self.assertEqual(2,self.measure()['completed_calls'])
    def test_wrong_request_session_stop_and_duplicate_are_rejected(self):
        d,r,frame=self.call('a',32)
        for changed in [dict(frame,id='4'),dict(frame,result=dict(frame['result'],sessionId='other')),
                        dict(frame,result=dict(frame['result'],stopReason='cancelled'))]:
            (d/'current.log').write_text(json.dumps(changed)+'\n')
            self.refix_logs(d)
            with self.assertRaises(AssertionError): self.measure()
        (d/'current.log').write_text((json.dumps(frame)+'\n')*2)
        self.refix_logs(d)
        with self.assertRaises(AssertionError): self.measure()
    def test_missing_partial_snapshot_and_negative_not_totals(self):
        d,r,frame=self.call('a',32)
        for usage in [{},{'totalTokens':32},{'inputTokens':32,'outputTokens':0,'totalTokens':-32}]:
            (d/'current.log').write_text(json.dumps(dict(frame,result=dict(frame['result'],usage=usage)))+'\n')
            with self.assertRaises(AssertionError): self.measure()
        (d/'current.log').write_text(json.dumps(frame)+'\n')
        self.refix_logs(d)
        for measurement in ['UNAVAILABLE','SESSION_USED_SNAPSHOT']:
            r['agentResultAcquisition']['occurrences'][0]['usage']['measurementType']=measurement
            self.fixed(d,r)
            with self.assertRaises(AssertionError): self.measure()
    def test_delayed_shared_result_is_strict_fallback_only(self):
        d,r,frame=self.call('a',32)
        (d/'current.log').write_text('')
        self.refix_logs(d)
        stream=self.sessions/(self.record+'.stream.ndjson')
        request={'id':'3','method':'session/prompt','params':{'sessionId':'s'}}
        pair=json.dumps(request)+'\n'+json.dumps(frame)+'\n'
        stream.write_text(pair)
        self.assertEqual(32,self.measure()['actual_tokens'])
        stream.write_text(pair+pair)
        with self.assertRaises(AssertionError): self.measure()
    def test_bad_current_cannot_be_washed_by_complete_history(self):
        d,r,frame=self.call('a',32)
        stream=self.sessions/(self.record+'.stream.ndjson')
        request={'id':'3','method':'session/prompt','params':{'sessionId':'s'}}
        stream.write_text(json.dumps(request)+'\n'+json.dumps(frame)+'\n')
        frame['result']['usage']={'totalTokens':32}
        (d/'current.log').write_text(json.dumps(frame)+'\n')
        with self.assertRaises(AssertionError): self.measure()
    def test_one_transcript_cannot_supply_two_calls(self):
        _,a,_=self.call('a',32)
        d,b,_=self.call('b',32)
        b['agentResultAcquisition']['occurrences'][0]=copy.deepcopy(a['agentResultAcquisition']['occurrences'][0])
        self.fixed(d,b)
        with self.assertRaises(AssertionError): self.measure()
    def test_missing_fixed_completion_and_log_drift_stop(self):
        d,r,frame=self.call('a',32)
        (d/'current.log').write_text(json.dumps(dict(frame,extra_trace='changed bytes'))+'\n')
        with self.assertRaisesRegex(AssertionError,'Fixed invocation log drift'): self.measure()
        self.refix_logs(d)
        (d/'outputs/fixture-completion.json').unlink()
        with self.assertRaisesRegex(AssertionError,'Missing or ambiguous fixed completion'): self.measure()
    def test_mismatched_fixed_amount_and_unfinished_run_stop(self):
        d,r,_=self.call('a',32)
        r['agentResultAcquisition']['occurrences'][0]['usage']['totalTokens']=33
        self.fixed(d,r)
        with self.assertRaises(AssertionError): self.measure()
        r['finishedAt']=None
        self.fixed(d,r)
        with self.assertRaises(AssertionError): self.measure()

if __name__ == '__main__': unittest.main()
