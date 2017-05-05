import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.args4j.Option;

import filesync.CopyBlockInstruction;
import filesync.Instruction;
import filesync.NewBlockInstruction;
import filesync.SynchronisedFile;




public class SyncClient {
	
	@Option(name="-f",usage="folder-path")
	private static String folderPath = "";
	@Option(name="-h")
	private static String hostName = "";
	
	private static SynchronisedFile fromFile;
	static DataInputStream in;
	static DataOutputStream out;
//	private static String folderPath = "D:\\Ffrom";
//	private static String hostName = "localhost";
	
	private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;
    static String eventKind;
    static String fileName;
    
    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    SyncClient(Path dir, boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;

        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }

    /**
     * Process all events for keys queued to the watcher
     */
    void processEvents() {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();
                eventKind = kind.name();
//                eventKind = eventKind + kind.name() + "/";
                
                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                
                Path child = dir.resolve(name);
                fileName = child.toString();
//                fileName = fileName + child.toString() + "/";
                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readbale
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    static void usage() {
        System.err.println("usage: java WatchDir [-r] dir");
        System.exit(-1);
    }
    
    
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		folderPath = args[1];
		hostName = args[3];
//		Socket socket = new Socket("localhost",4444);
		Socket socket = new Socket(hostName,4444);
		in = new DataInputStream(socket.getInputStream());
		out = new DataOutputStream(socket.getOutputStream());
		
		for(String file : new File(folderPath).list()){
			String name = new File(file).getName();
//			String filePath = folderPath + "\\" + fileName;
//			fromFile = new SynchronisedFile(filePath,1024);
			Thread t = new Thread(new Runnable(){

				@Override
				public void run() {
					// TODO Auto-generated method stub
					checkFileExist(name);
				}
				
			});
//			t.setName(fileName);
			t.setDaemon(true);
			t.start();
			
		}
		
		Thread t = new Thread(new Runnable(){
			public void run(){
				while(true){
					try {
						Thread.sleep(1000);
						if(eventKind.equals("ENTRY_CREATE")){
//							System.out.println("Create! ");
//							out.writeUTF("Client Test Message " + eventKind + fileName);
							out.writeUTF(eventKind + "/" + fileName);
							out.flush();
							eventKind = "ENTRY_MODIFY";
//							eventKind = null;
//							modify(fromFile);
						}else if(eventKind.equals("ENTRY_MODIFY")){
//							System.out.println("Modify! "+eventKind + fileName);
							out.writeUTF(eventKind + "/" + fileName);
							out.flush();
							fromFile = new SynchronisedFile(fileName,1024);
							modify(fromFile);
							eventKind = null;
							fileName = null;
							
						}else if(eventKind.equals("ENTRY_DELETE")){
//							System.out.println("Delete! ");
							out.writeUTF(eventKind + "/" + fileName);
							out.flush();
							eventKind = null;
							fileName = null;
						}
						
					
						// Blocks until server responds
//						String response = in.readUTF();
//						System.out.println(response);
						} catch (Exception e) {
							// TODO Auto-generated catch block
//							e.printStackTrace();
						}
				}

			}
		});
	
    
		t.start();
		Path path = Paths.get(folderPath);
		new SyncClient(path,false).processEvents();
	}
	
	public static void  modify(SynchronisedFile fromFile){
//		System.out.println("In modify()");
		try {
			fromFile.CheckFileState();
			Instruction inst;
			while((inst=fromFile.NextInstruction())!=null){
				String msg=inst.ToJSON();
//				System.out.println("Here!!!!!!"+msg);
				out.writeUTF(msg);
				out.flush();
				String back = in.readUTF();
//				System.out.println(back);
				if(back.equals("NewBlock")){
//					System.out.println("in if");
					Instruction upgraded=new NewBlockInstruction((CopyBlockInstruction)inst);
					String msg2 = upgraded.ToJSON();
//					System.out.println("msg2"+msg2);
					out.writeUTF(msg2);
					out.flush();
					System.err.println("Sending: "+msg2);
				}else if(back.contains("End")){
					break;
				}
//			break;
			}
			System.out.println("Backup success");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
		}
	}
	
	public static void checkFileExist(String name){
		try {
			out.writeUTF("CheckFile/"+name);
			System.out.println("Check file "+name);
			out.flush();
			String readName = in.readUTF();
//			System.out.println(readName);
			if(readName.equals("true")){
				eventKind = "ENTRY_MODIFY";
				fileName = folderPath + "\\" + name;
			}else if(readName.equals("false")){
				eventKind = "ENTRY_CREATE";
				fileName = folderPath + "\\" + name;
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
