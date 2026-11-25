# Offline diagnostic runner while unrelated runtime fixtures do not compile.
# Compiles only the named permanent tests; never represents the full Maven suite.
from pathlib import Path
import xml.etree.ElementTree as E
import subprocess
root=Path(__file__).resolve().parents[3]
base=Path(__file__).resolve().parent
report=root/'dual-ledger-file/target/surefire-reports/TEST-dev.dual.ledger.file.WorkflowCommitSequenceTest.xml'
cp=next(p.attrib['value'] for p in E.parse(report).findall('properties/property') if p.attrib['name']=='java.class.path')
launcher=Path.home()/'.m2/repository/org/junit/platform/junit-platform-launcher/1.11.4/junit-platform-launcher-1.11.4.jar'
assert launcher.is_file(), 'pinned JUnit launcher unavailable; no download permitted'
classes=base/'focused-classes'; classes.mkdir(exist_ok=True)
cp=':'.join([str(classes),str(root/'dual-runtime/target/classes'),str(root/'dual-adapter-git/target/classes'),cp,str(launcher)])
sources=[root/'dual-runtime/src/test/java/dev/dual/runtime/handler/VerificationReadinessTest.java',
 root/'dual-runtime/src/test/java/dev/dual/runtime/handler/ReadinessRoutingDefinitionTest.java',
 root/'dual-runtime/src/test/java/dev/dual/runtime/handler/PhasedContractContinuationTest.java',
 root/'dual-runtime/src/test/java/dev/dual/runtime/handler/AgentOutputLineageFixture.java',base/'FocusedTests.java']
subprocess.run(['javac','--release','21','-cp',cp,'-d',str(classes),*[str(p) for p in sources]],check=True)
subprocess.run(['java','-cp',cp,'FocusedTests','dev.dual.runtime.handler.VerificationReadinessTest','dev.dual.runtime.handler.ReadinessRoutingDefinitionTest','dev.dual.runtime.handler.PhasedContractContinuationTest'],check=True)
