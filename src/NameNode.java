import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NameNode {
	final static int MB = 4194304;//String cut in 4MB
	//hashmap that stores the filename and DNum+BNum
	static Map<String, List<Pair>> map=new HashMap<String, List<Pair>>(); 
	//ArrayList that contain all the substring of contents

	private ServerSocket serverSocket;
	private Socket clientSocket;
	private PrintWriter out;
	private BufferedReader in;
	//mutex
	//private final Object mutex = new Object();


	public static void main(String[] args) {
		//Append("First.txt","Hello!"); //Append only when it does not exist, otherwise deny it
		NameNode server = new NameNode();
		server.start(5558);
	}

	public void start(int port) {
		try {
			serverSocket = new ServerSocket(port);
			while (true) {
				new NameNodeHandler(serverSocket.accept()).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void stop() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static class NameNodeHandler extends Thread { //Name Node Handler is created on every request from the client. 
		static NameNodeHandlerClient ctoD;					//.. and closed once request is completed
		public class NameNodeHandlerClient {
			private Socket clientSocket;
			private PrintWriter out;
			private BufferedReader in;
			
			public void startConnection(String ip, int port) {
				try {
					clientSocket = new Socket(ip, port);
					out = new PrintWriter(clientSocket.getOutputStream(), true);
					in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			public String sendMessage(String msg) {
				try {
					out.println(msg);
					String resp = in.readLine();
					return resp;
				} catch (IOException e) {
					e.printStackTrace();
				}
				return "";
			}

			public void stopConnection() {
				try {
					in.close();
					out.close();
					clientSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		private static Socket clientSocket;
		private PrintWriter out;
		private BufferedReader in;


		// ---- START section that actually implements the name node handler ----- //
		public NameNodeHandler(Socket socket) {
			this.clientSocket = socket;
		}

		public void run() {
			try {
				out = new PrintWriter(clientSocket.getOutputStream(), true);
				in = new BufferedReader(
						new InputStreamReader(clientSocket.getInputStream()));

				String inputLine;
				while ((inputLine = in.readLine()) != null) {//BEGIN while loop
					if (".".equals(inputLine)) {		//if a dot is sent, close everything
						break;
					}
					ctoD = new NameNodeHandlerClient();	//Create the client that will be used repeatedly to communicate with datanodes 
					
					//---Parse String, Call Read or Append --//
					String[] tokens = inputLine.split(" ");
					String file;
					if(tokens[0].toLowerCase().equals("read") && tokens.length == 2) {
						file = tokens[1];
						
						Read(file);
						//once we've finished reading, we're done
						break;
					}
					else if(tokens[0].toLowerCase().equals("append") && tokens.length >= 3) {
						file = tokens[1];
						String[] contents = inputLine.split(" ", 3);
						String cont = contents[2];				//gets the contents of the string including spaces

						Append(file,cont);
					}
					else
					{
						System.out.println("Name Node ERROR: Failed to parse string in NameNode");
					}
					out.println(inputLine);
				}//END while loop, close connection

				in.close();
				out.close();
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		//---------   The following are the "NameNode" functions ----------- //
		public static void Append(String filename, String content) //filename ends in txt, content is one big string
		{	
			int blockNum = 0;
			List<String> subString = new ArrayList<String>(); //break down strings
			if(content.length()*2>MB) {
				blockNum = MB/content.length()+1; 
			}
			else {
				blockNum = 1;
			}

			for(int i = 0; i < blockNum; i++)//cut string by by
			{
				int idx1 = i*MB;
				int idx2 = (i+1)*MB;
				if(idx2 > content.length()) {
					idx2 = content.length();
				}
				
				subString.add(content.substring(idx1, idx2));
			}


			String returnID = null; //the message giving back
			String success = null;
			List<Pair> list = new ArrayList<Pair>();
			int NumBlocksReceived = 0;
			int DNDirector = 0;
			while(NumBlocksReceived < blockNum)
			{
				///////////////////////////////////////////////////////////////////////////////////////////////////////////////
				//reqest send to DN1, DN2, DN3
				//talk to DNDirect%3(th) DataNode
				//EX: 5 blockNum, request to DN1,DN2,DN3,DN1,DN2
				//if a freeblock is given back, set gotit to true
				
				
				if(DNDirector%3 == 0)
				{
					ctoD.startConnection("127.0.0.1", 65530); //Instantiate DataNode 1
					returnID = ctoD.sendMessage("Alloc");
					ctoD.stopConnection();
					if(returnID.equals("-1")) 
					{DNDirector++;}//-1 ,parse it to int 
					else
					{
						Pair pair = new Pair("D1",Integer.parseInt(returnID));
						list.add(pair);
						String msg = "Write "+returnID+ " "+ subString.get(NumBlocksReceived);
						ctoD.startConnection("127.0.0.1", 65530);
						success = ctoD.sendMessage(msg);
						ctoD.stopConnection();
						NumBlocksReceived++;
					}
				}
				else if(DNDirector%3 == 1)
				{
					ctoD.startConnection("127.0.0.1", 65531);
					returnID = ctoD.sendMessage("Alloc");
					ctoD.stopConnection();

					if(returnID.equals("-1")) 
					{DNDirector++;}//-1 ,parse it to int 
					else
					{
						Pair pair = new Pair("D2",Integer.parseInt(returnID));
						list.add(pair);
						ctoD.startConnection("127.0.0.1", 65531);
						success = ctoD.sendMessage("Write "+returnID+ " "+ subString.get(NumBlocksReceived));
						ctoD.stopConnection();
						NumBlocksReceived++;
					}
				}
				else if(DNDirector%3 == 2)
				{
					ctoD.startConnection("127.0.0.1", 65532);
					returnID = ctoD.sendMessage("Alloc");
					ctoD.stopConnection();

					if(returnID.equals("-1")) 
					{DNDirector++;}//-1 ,parse it to int 
					else
					{
						Pair pair = new Pair("D3",Integer.parseInt(returnID));
						list.add(pair);
						ctoD.startConnection("127.0.0.1", 65532);
						success = ctoD.sendMessage("Write "+returnID+ " "+ subString.get(NumBlocksReceived));
						ctoD.stopConnection();
						NumBlocksReceived++;
					}
				}

			}
			if(map.containsKey(filename)){
				map.get(filename).addAll(list);
			}
			else {
			map.put(filename, list);//saves in the hash table
			}
			List<Pair> test = map.get(filename);
		}


		public static void Read(String filename)
		{
			String content = null;
			List<Pair> temp = new ArrayList<Pair>();
			List<String> conCat = new ArrayList<String>(); //concatenate strings

			if(map.containsKey(filename))//if the file is found
			{
				temp = map.get(filename);	
				for(int j = 0; j < temp.size(); j++)//add mutex to this???
				{
					//File infile =new File(arr[index][j]);
					temp.get(j).getdataNode();
					//send a message to the specific data node
					//give back the centent of block note
					if(temp.get(j).getdataNode().equals("D1"))
					{
						ctoD.startConnection("127.0.0.1", 65530);
						content = ctoD.sendMessage("Read " + Integer.toString(temp.get(j).getblockNode()));
						ctoD.stopConnection();
						conCat.add(j,content);
					}
					if(temp.get(j).getdataNode().equals("D2"))
					{
						ctoD.startConnection("127.0.0.1", 65531);
						content = ctoD.sendMessage("Read " + Integer.toString(temp.get(j).getblockNode()));
						ctoD.stopConnection();
						conCat.add(j,content);
					}
					if(temp.get(j).getdataNode().equals("D3"))
					{
						ctoD.startConnection("127.0.0.1", 65532);
						content = ctoD.sendMessage("Read " + Integer.toString(temp.get(j).getblockNode()));
						ctoD.stopConnection();
						conCat.add(j,content);
					}
				}
				String joined = String.join(" ", conCat); //conCat is a string list
				//joined is send back to client
				output(joined);

			}else{
				System.out.println("Name Node ERROR: The file does not exist.");
			}
		}
		private static void output(String out) {
			try {
				PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
				pw.flush();
				pw.print(out);
				pw.flush();
				pw.close();
			} catch (Exception e) {

			}
		}
	}	//END NAME NODE HANDLER


}//END NAME NODE




