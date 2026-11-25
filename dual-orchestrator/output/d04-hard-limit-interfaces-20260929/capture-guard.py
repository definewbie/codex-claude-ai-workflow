def checked_final(item, request_count):
    if request_count != 1:
        raise RuntimeError('REQUEST_LIMIT')
    if set(item) != {'type','id','role','status','content'} or item['type'] != 'message' or item['role'] != 'assistant' or item['status'] != 'completed':
        raise RuntimeError('NON_FINAL_RESPONSE')
    if item['content'] != [{'type':'output_text','text':'CAPTURE_DONE','annotations':[]}]:
        raise RuntimeError('UNEXPECTED_CONTENT')
    return item
