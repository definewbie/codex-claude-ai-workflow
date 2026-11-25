import java.nio.file.*;
import java.nio.channels.*;
import static java.nio.file.StandardOpenOption.*;
public class StorageProbe {
  public static void main(String[] args) throws Exception {
    Path dir=Path.of(args[0]);
    if(args.length>1){
      try(var ch=FileChannel.open(dir.resolve("lock"),CREATE,WRITE)){
        try(var lock=ch.tryLock()){System.exit(lock==null?10:11);}
      }
    }
    Files.createDirectories(dir);
    Path target=dir.resolve("target"), temp=dir.resolve("target.tmp");
    Files.writeString(target,"before");Files.writeString(temp,"after");
    Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    if(!Files.readString(target).equals("after")||Files.exists(temp))throw new AssertionError("move");
    boolean overlap=false;int child;
    try(var first=FileChannel.open(dir.resolve("lock"),CREATE,WRITE);var held=first.tryLock();var second=FileChannel.open(dir.resolve("lock"),CREATE,WRITE)){
      try(var extra=second.tryLock()){throw new AssertionError("same JVM lock acquired twice");}
      catch(OverlappingFileLockException expected){overlap=true;}
      var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),System.getProperty("probe.source"),dir.toString(),"child").inheritIO().start();
      if(!process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("child timeout");}
      child=process.exitValue();if(child!=10)throw new AssertionError("cross process lock "+child);
    }
    try(var ch=FileChannel.open(dir.resolve("lock"),CREATE,WRITE);var held=ch.tryLock()){
      if(held==null)throw new AssertionError("lock reacquire");
    }
    System.out.println("{\"status\":\"PASS_STORAGE_PRIMITIVES\",\"atomicReplace\":true,\"sameJvmOverlapRejected\":"+overlap+",\"crossJvmBlockedExit\":"+child+",\"reacquire\":true}");
  }
}
