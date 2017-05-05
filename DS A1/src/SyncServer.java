import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;

import org.kohsuke.args4j.Option;

import filesync.*;


public class SyncServer {
	
	@Option(name="-f",usage="folder-path")
	private static String folderPath = "";
	
	static SynchronisedFile toFile;
	static DataInputStream in;
	static DataOutputStream out;
//	static String folderPath = "D:\\Fto";
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		folderPath = args[1];
		ServerSocket serverSocket = new ServerSocket(4444);
		System.out.println("Server start");
		Socket socket = serverSocket.accept();
		in = new DataInputStream(socket.getInputStream());
		out = new DataOutputStream(socket.getOutputStream());
		while(true) {
			String message = in.readUTF();
			if(message.contains("ENTRY_CREATE")){
//				System.out.println(message);
				String[] s = message.split("/");
				Path path = Paths.get(s[1]);
				String fileName = path.getFileName().toString();
				String filePath = folderPath + "\\" + fileName;
				creatFile(filePath);
				System.out.println("File "+fileName+" Created!");
			}else if(message.contains("ENTRY_DELETE")){
//				System.out.println(message);
				String[] s = message.split("/");
				Path path = Paths.get(s[1]);
				String fileName = path.getFileName().toString();
//				System.out.println(fileName);
				String filePath = folderPath + "\\" + fileName;
				Path deletePath = Paths.get(filePath);
				try {
				    Files.delete(deletePath);
				    System.out.println("File "+fileName+" Deleted!");
				} catch (NoSuchFileException x) {
				    System.err.format("%s: no such" + " file or directory%n", deletePath);
				} catch (IOException x) {
				    // File permission problems are caught here.
				    System.err.println(x);
				}
			}else if(message.contains("ENTRY_MODIFY")){
				String[] s = message.split("/");
				Path path = Paths.get(s[1]);
				String fileName = path.getFileName().toString();
				String filePath = folderPath + "\\" + fileName;
				toFile = new SynchronisedFile(filePath, 1024);
				while(true){
					String blockMsg = in.readUTF();
//					System.out.println("blockMsg"+blockMsg);
					
					InstructionFactory instFact = new InstructionFactory();
					Instruction receivedInst = instFact.FromJSON(blockMsg);
					try {
						toFile.ProcessInstruction(receivedInst);
						if(blockMsg.contains("EndUpdate")){
							
							out.writeUTF("End");
							out.flush();
							break;
						}
						out.writeUTF("OK");
						out.flush();
						
					} catch (IOException e) {
						e.printStackTrace();
						System.exit(-1); // just die at the first sign of trouble
					} catch (BlockUnavailableException e){
						out.writeUTF("NewBlock");
						out.flush();
						String msg2 = in.readUTF();
						Instruction receivedInst2 = instFact.FromJSON(msg2);
						try {
							toFile.ProcessInstruction(receivedInst2);
						} catch (BlockUnavailableException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
			}
			else if(message.contains("CheckFile")){
//				System.out.println(message);
				String[] s = message.split("/");
				boolean b = checkFilesName(s[1]);
//				System.out.println(b);
				if(b){
					out.writeUTF("true");
					out.flush();
				}else{
					out.writeUTF("false");
					out.flush();
				}
			}
		}
	}
	
	public static boolean creatFile(String filePath) throws IOException{
		boolean flag = false;
		File file = new File(filePath);
		file.createNewFile();
		flag = true;
		return flag;
		
	}
	public static boolean checkFilesName(String fromName){
		ArrayList<String> nameList = new ArrayList<String>();
		for(String file : new File(folderPath).list()){
			String name = new File(file).getName();
			nameList.add(name);
		}
			return nameList.contains(fromName);
	}

}
