import java.io.*;
import java.net.*;
import java.lang.*;


public class EchoClient{



	public static void main(String[] args){
	
		//variable initialization
		String lineIn = null;  //client string to send to server
		String lineOut = null;  //response from server
		String hostname = null;  //hostname of server
		String tempPort = null;	//port number as a string to be parsed to int
		int portNum = 0;	//port number
		int count = 0;		//counter for arguments
		Socket clientSocket = null;	


		for(String arguments: args){     //gathers args and puts them in variables
			if(count == 0){
				hostname = arguments;
			}else{
				tempPort = arguments;
			}
			count++;
		}	
		
		portNum = Integer.parseInt(tempPort);   //parses the port number into an int
			

		try{
			//creates client instream
			BufferedReader inStream = new BufferedReader(new InputStreamReader(System.in));
			//socket setup
			clientSocket = new Socket(hostname, portNum);
		
			//creates server instream
			DataOutputStream outStream = new DataOutputStream(clientSocket.getOutputStream());
		
			//creates stream for server reply
			BufferedReader serverReply = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		
			//client sending info to server and server responding
			lineIn = inStream.readLine();
			outStream.writeBytes(lineIn+'\n');
			lineOut = serverReply.readLine();
			
			StringBuilder build = new StringBuilder();
			build.append(serverReply.readLine()+" 1 \n");
			build.append(serverReply.readLine()+" 2 \n");
			build.append(serverReply.readLine()+" 3 \n");
			build.append(serverReply.readLine()+" 4 \n");
			
			lineOut = build.toString();

			System.out.println(lineOut);
			//closing up shop
			clientSocket.close();
			inStream.close();
			outStream.close();
			serverReply.close();

		}catch(Exception e){
			System.out.println("Exception"+ e);
			return;
		

		}
		return;
	}
}

