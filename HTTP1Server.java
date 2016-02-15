
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This program implements a partial HTTP 1.0 web server to which clients can send specific HTTP requests, and receive the appropriate response from the server, including request issues, commands not impelmented, whether or not a file exists, etc.  
 * 
 * @author Alex Rossi, Srinayani Marpaka
 *
 */
public class HTTP1Server {
	
	/*
	 * This main method takes in one argument from the user, the port number on which the socket should be created and to which others can connect. It then validates the input, ensuring
	 * that the argument entered is an integer and the appropriate number of inputs have been entered. 
	 */

	public static void main(String[] args) {
		
		int port;
		//Checking how many arguments are presented in order to make sure that the server socket can be created
		if (args.length == 1){
			//Parsing the argument into an integer, and if the port is not an integer, then the program exits.
			try{
				port = Integer.parseInt(args[0]);
			}catch(NumberFormatException e){
				System.err.println("The argument must be an integer.");
				return;
			}
			buildServerSocket(port);
			return;
		}else{
			System.err.println("Please provide only the port number.");
			return;
		}	

	}
	
	/*
	 * This method actually creates a new server socket to which clients can connect and send their requests to. In addition to this, it also accepts connections from clients, having the capability to run 50 threads simultaneously, or perform 50 tasks at once. 
	 * When a connection is accepted from a client, a thread is then executed (the CommunicationThread) which actually performs the further tasks such as reading user input and giving additional responses.
	 */ 
	public static void buildServerSocket(int port){
		ServerSocket server;
		//Building the server socket, and displays when the socket is actually open to accept connections. Otherwise, displays an error message and terminates the program so as 
		//not to attempt to accept any connections. 
		try {
			server = new ServerSocket(port);
			System.out.println("Accepting Connections:"+"\n");
		} catch (IOException e) {
			System.err.println("IO Exception: Cannot build the server socket. ");
			return;
		}
		/*This portion creates a threadpoolexecutor, which can be used in order to run multiple tasks simultaneously and cap it off at a certain number of threads, which in this case is 50. As such, the 51st case will be dropped because we are using the SynchronousQueue.As clients request connections, a thread will be utilized.
		 * In this situation, we set the core pool size to be 5 threads, the max number of threads to be 50, and a SynchronousQueue implementation.  
		 */
		ThreadPoolExecutor mainthreadpool = new ThreadPoolExecutor(5, 50, 10, TimeUnit.MICROSECONDS, new SynchronousQueue<Runnable>());
		/*
		 * This portion of the code initializes the DataOutputStream, connection from the client and the BufferedReader to read the client's input.
		 * These are all initialized at this point in the event that all threads are busy, the connections can be closed here without having to enter
		 * the communicationthread. 
		 */
		BufferedReader clientMessage = null;
		DataOutputStream outToClient = null;
		Socket connectionSocket = null;
		
		/*
		 * This portion of the code sets up the sockets, and the streams respectively. Then the thread is executed or essentially started
		 * with the mainthreadpool.executure line as a new CommunicationThread is created in order to get the cient's request and respond with an appropriate message.
		 * This is while(tue) because the server socket willbe open for things to connect to until it is closed.
		 */
		
		while (true){
			try {
				
				//Sets up the sockets, streams, and threads
				connectionSocket = server.accept();
				clientMessage = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
				outToClient = new DataOutputStream(connectionSocket.getOutputStream());				
				mainthreadpool.execute(new CommunicationThread(connectionSocket, outToClient, clientMessage, "127.0.0.1", String.valueOf(port)));

			} catch(RejectedExecutionException e){
				/*
				 * This portion of the code is reached when there are 50 threads that are running, so 50 tasks and that means that the next task has to be droppd
				 * as this server can only accomodate upto 50 tasks. At this point, the 503 service unavailable message is sent to the client and all further connections
				 * with the client are closed.
				 */
				try {
					
					outToClient.writeBytes("HTTP/1.0 503 Service Unavailable");
					outToClient.flush();
					try {
						Thread.currentThread().sleep(500);
					} catch (InterruptedException e1) {
						
						System.err.println("InterruptedException: Waiting time interrupted.");
					}
					
					try{
						
						outToClient.close();
						clientMessage.close();
						connectionSocket.close();
						
					}catch(IOException e2){
						
						System.err.println("IOException in closing the streams/socket");
					}
					
				} catch (IOException e1) {
					
					System.err.println("IO Exception: Error writing messages.");
				}
				
			}catch (IOException e) {
				
				System.err.println("IO Exception: Error in connection socket. ");
				return;
			}
		}	
	}
}

/*
 * This class represents the runnable thread class for all of the client communications to be handled in when a client is connected to the server. 
 * In this class, the client requests are read, and responses are sent back to the client with the correct HTTP status code, headers an content.
 */
class CommunicationThread implements Runnable{
	Socket connectionSocket;
	
	DataOutputStream outToClient;
	BufferedReader clientMessage;
	String SERVER_PORT;
	String SERVER_NAME;
	//Creates a new thread passing the client connectionsocket, so everything that is done in this thread will be pertinent to this client connection
	public CommunicationThread(Socket connectionSocket, DataOutputStream outToClient, BufferedReader clientMessage, String ServerName, String ServerPort){
		
		this.connectionSocket = connectionSocket;
		this.clientMessage = clientMessage;
		this.outToClient = outToClient;
		this.SERVER_PORT = ServerPort;
		this.SERVER_NAME = ServerName;
	}
	
	
	/*
	 * Run method for the thread, which actually takes in the client input, parses the input based on the spaces, and then attempts to get what the client is asking for or returns the 
	 * appropriate http response. A connection timeout is also set for the client socket, so that that particular connection is closed if the client does not enter anything for three seconds,
	 * and this in particular returns the 408 request timeout response.
	 */
	
	@Override
	public void run() {
		String CONTENT_LENGTH = null;
		String SCRIPT_NAME = null;
		
		String HTTP_FROM = null;
		String HTTP_USER_AGENT = null;
		String postString = "";
		
		String clientInput;
		String command = null;
		String path = null;
		boolean validILM = false;
		boolean clheader = false;
		boolean ctheader = false;
        
        int postContentLength = 0;
		int loopCount = 1;	//Keeps track of the request line
		boolean LM = false;	//Checks if If-Modified-Since is valid
		long modSince = 0;	//Date of the IMS condition
		
		boolean skipline = false;
		
		
		//The following portion of the code is responsible creating the different datastreams connecting the server socket to the client socket in orde to be able to pass messages from one 
		//to the other
		try {
			
			//This sets the timeout to 3000 (ie - if there is no input in 3000 milliseconds, then the client socket will throw the socketTimeoutexception, writing the bad reques http response
			//to the output and closing the associated sockets/streams. 
			connectionSocket.setSoTimeout(3000);
			/* This while loop continually read input from the client until a blank line is encountered at which the loop is exited or if there is a bad request or command not implemented which would also lead to a response to the client and the connections closing. 			
			 */
			while(true){
			
				//Gonna parse some lines
				clientInput = clientMessage.readLine();
				StringTokenizer tokens = null;
				if(!clientInput.equals("")){
					tokens = new StringTokenizer(clientInput);	
				}
				//First iteration checks for the standard stuff: <command> <path> HTTP/x.y
				if(loopCount == 1 && !clientInput.equals("")){
					
					//checks for proper number of tokens for first line
					if(tokens.countTokens() != 3){
						//Bad request
						outToClient.writeBytes("HTTP/1.0 400 Bad Request");
						outToClient.flush();
						/*
						 * Here and elsewhere, the following portion of the code is meant to wait 0.5 seconds before closing all of the respective connections. 
						 */
						try {
							Thread.currentThread().sleep(500);
						} catch (InterruptedException e1) {
							
							System.err.println("InterruptedException: Waiting time interrupted.");
						}
						
						closeConnections();
						return;
						
					}else{
						
						String x = tokens.nextToken();
						command = x;
						//If the command isn't one of the designated 6 that are allowed, then it automatically becomes a bad request regardless of any other errors that may be present because this 400 level error is greater than the other ones.
						if(!(x.equals("DELETE") || x.equals("PUT") || x.equals("LINK") || x.equals("UNLINK") || x.equals("POST") || x.equals("GET") || x.equals("HEAD"))){
							outToClient.writeBytes("HTTP/1.0 400 Bad Request");
							outToClient.flush();
							
							try {
								Thread.currentThread().sleep(500);
							} catch (InterruptedException e1) {
								
								System.err.println("InterruptedException: Waiting time interrupted.");
							}
							
							closeConnections();
							return;						
						}
						//Gets the path and makes sure it's valid
						//This checks the format of the next token (the link) in order to ensure that it is properly formatted. If not, it also results in a bad request and closes all connections. 
						x = tokens.nextToken();
						if(!x.startsWith("/")){
							
							outToClient.writeBytes("HTTP/1.0 400 Bad Request");
							outToClient.flush();
							
							try {
								Thread.currentThread().sleep(500);
							} catch (InterruptedException e1) {
								
								System.err.println("InterruptedException: Waiting time interrupted.");
							}
							
							closeConnections();
							return;
							
						}else{
							path = x;
							SCRIPT_NAME = path;
							
						}						
						
						
						x = tokens.nextToken();
						//This checks that the final part of the command with the HTTP version is properly formatted, first checking the "/" and then making sure it is an HTTP request. If either of these are not fulfilled, then it is a 400 bad request.
						if(!x.contains("/")){
							
							outToClient.writeBytes("HTTP/1.0 400 Bad Request");
							outToClient.flush();
							
							try {
								Thread.currentThread().sleep(500);
							} catch (InterruptedException e1) {
								
								System.err.println("InterruptedException: Waiting time interrupted.");
							}
							
							closeConnections();
							return;
							
						}else{
							
							String[] req = x.split("/");
							if(!req[0].equals("HTTP")){
								
								outToClient.writeBytes("HTTP/1.0 400 Bad Request");
								outToClient.flush();
								
								try {
									Thread.currentThread().sleep(500);
								} catch (InterruptedException e1) {	
									
									System.err.println("InterruptedException: Waiting time interrupted.");
								}
								
								closeConnections();
								return;
							}
							
							try{
								/*
								 * This portion parses the float after HTTP/ in order to check the version and ensure that it is actually a float. If it is not a float, then this is an error as that would be a bad request and is caught with the NumberFormatException. 
								 */
								float y = Float.parseFloat(req[1]);
								
								if(y > 1.0){
									//Otherwise, if the number is a float but is greater than 1.0 then it is a version that is not supported by this particular web server. 
									outToClient.writeBytes("HTTP/1.0 505 HTTP Version Not Supported");
									outToClient.flush();
									
									try {
										Thread.currentThread().sleep(500);
									} catch (InterruptedException e1) {		
										
										System.err.println("InterruptedException: Waiting time interrupted.");
									}
									
									closeConnections();
									return;
								}
								
							}catch(NumberFormatException e){
								outToClient.writeBytes("HTTP/1.0 400 Bad Request");
								outToClient.flush();
								
								try {
									Thread.currentThread().sleep(500);
								} catch (InterruptedException e1) {	
									
									System.err.println("InterruptedException: Waiting time interrupted.");
								}
								
								closeConnections();
								return;
							}
								
							//Assures that the request command is implemented, so essentially if the command is DELETE or UNLINK or LINK or PUT, then that would result in a 501 Not Implemented. 
							if(!command.equals("POST") && !command.equals("HEAD") && !command.equals("GET")){
								outToClient.writeBytes("HTTP/1.0 501 Not Implemented");
								outToClient.flush();
								
								try {
									Thread.currentThread().sleep(500);
								} catch (InterruptedException e1) {									
									
									System.err.println("InterruptedException: Waiting time interrupted.");
								}
								
								closeConnections();
								return;
							}
							if(command.equals("POST") && path.contains(".cgi") ==false){
								outToClient.writeBytes("HTTP/1.0 405 Method Not Allowed");
								outToClient.flush();
								try {
									Thread.currentThread().sleep(500);
								} catch (InterruptedException e1) {
									
									System.err.println("InterruptedException: Waiting time interrupted.");
								}
								
								closeConnections();
								return;
							}
						}							
					}
					
					loopCount++;  //Allows the code below to be executed on next iteration (so reads further lines)
					
					}else{
						//This portion is reached with all lines following the first.
						//Checks if the request is done
						
						if(clientInput.equals("") && !command.equals("POST")){
							break;
						}else if("".equals(clientInput)){
							
						}
						/*else if(clientInput.equals("") && skipline == false && command.equals("POST")){
							System.out.println("INP "+clientInput);
							skipline = true;
							continue;
						}*/
						
						//Makes sure that there aren't more than 2 lines of actual text in the request if the command is POST. 
						if(loopCount > 2 && command.equals("POST") == false){
							
							outToClient.writeBytes("HTTP/1.0 400 Bad Request");
							outToClient.flush();
							
							try {
								Thread.currentThread().sleep(500);
							} catch (InterruptedException e1) {		
								
								System.err.println("InterruptedException: Waiting time interrupted.");
							}
							
							closeConnections();
							return;
						}
						

						//Handles If-Modified-Since
						
						String x = null;
						if(!clientInput.equals("")){
						x = tokens.nextToken();
						}
						
						if("If-Modified-Since:".equals(x)){
							
							LM = true;
							SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");	
							format.setTimeZone(TimeZone.getTimeZone("GMT"));
							String lastDate;
													
							x = tokens.nextToken();
							lastDate = x;
							
							try{
								
								//creates a string that is the date of the If-Modified-By statement
								while(tokens.hasMoreTokens()){
									x = tokens.nextToken();
									lastDate += " "+x;
								}
								
								//Creates a date for If-Modified-Since and converts it to Milliseconds
								Date date = format.parse(lastDate);
								modSince = date.getTime();
	
							}catch(Exception b){
								validILM = false;    //checks if the date format is correct, if there are enough tokens, etc.
							}
						}
					
						if(command.equals("POST")){
						
							if(clientInput.equals("")){
								
								if(clientMessage.ready()){
									clientInput = clientMessage.readLine();
								}else{
									break;
								}
								
								if(clientInput.equals("")){
									break;
								}
								
								//if(clientInput != null){
									postString = clientInput;
									
									break;
								//}else{
								//	break;
								//}
							}
							
							//Sets the environmental variable HTTP_FROM
							if("From:".equals(x)){
								//System.out.println("FROMMM "+);
								if(tokens.countTokens() == 1){
									HTTP_FROM = tokens.nextToken();
									
								}
							}
							
							//Sets the environmental variable HTTP_USER_AGENT
							if("User-Agent:".equals(x)){
								//if(tokens.countTokens() == 2){
									
									if(tokens.hasMoreTokens()){
										x=tokens.nextToken();
										HTTP_USER_AGENT = x;
										while(tokens.hasMoreTokens()){
											x = tokens.nextToken();
											HTTP_USER_AGENT += " "+x;
											
										}
									}
								//}
							}
							
							if("Content-Type:".equals(x)){
								
								x = tokens.nextToken();
								
								if(("application/x-www-form-urlencoded".equals(x)) == false){
									try {
										//System.err.println("408 Bad Request");
										outToClient.writeBytes("HTTP/1.0 500 Internal Server Error");
										outToClient.flush();
										
										//Each of these, from this and the future ones all put the thread to sleep after the flush for 500 milliseconds or half a second is done to ensure that all of the data has gone through. 
										try {
											Thread.currentThread().sleep(500);
										} catch (InterruptedException e1) {					
											
											System.err.println("InterruptedException: Waiting time interrupted.");
										}
										
										closeConnections();
										return;
										
									} catch (IOException e1) {	
										System.err.println("IO Exception: Error writing messages.");
									}
								}
								ctheader = true;
								
							}
							if("Content-Length:".equals(x)){
								//do something with length.
		                       
								//checks for the proper number of tokens in this header line
		                        if(tokens.countTokens() != 1){
		                        	
		                            try {
		                                //System.err.println("400 Bad Request");
		                                outToClient.writeBytes("HTTP/1.0 411 Length Required");
		                                outToClient.flush();
		                                
		                                //Each of these, from this and the future ones all put the thread to sleep after the flush for 500 milliseconds or half a second is done to ensure that all of the data has gone through.
		                                try {
		                                    Thread.currentThread().sleep(500);
		                                } catch (InterruptedException e1) {
		                                    
		                                    System.err.println("InterruptedException: Waiting time interrupted.");
		                                }
		                                
		                                closeConnections();
		                                return;
		                                
		                            } catch (IOException e1) {
		                                System.err.println("IO Exception: Error writing messages.");
		                            }
		                        }
							
							//sets x to the numeric length of the content as a string
								x = tokens.nextToken();
								
								//checks that the content length is a valid numeric value
								try{
									postContentLength = Integer.parseInt(x);
									
									
								}catch(NumberFormatException badNum){
									try {
										//System.err.println("408 Bad Request");
										outToClient.writeBytes("HTTP/1.0 411 Length Required");
										outToClient.flush();
		                                
										try {
											Thread.currentThread().sleep(500);
										} catch (InterruptedException e1) {					
											
											System.err.println("InterruptedException: Waiting time interrupted.");
										}
										
										closeConnections();
										return;
										
									} catch (IOException e1) {	
										System.err.println("IO Exception: Error writing messages.");
									}
								}
								
								
								
								clheader = true;
							}
							
							
						loopCount++;
					
						/*if(skipline =true){
							System.out.println("BREAKING");
							break;
						}*/
					}																					
				}
			}
			
			
		//Goes to this block if a timeout occurs and the client does not input anything within 3 seconds.
		}catch(SocketTimeoutException e){
			
			//System.err.println("408 Bad Request");
			try {
				//System.err.println("408 Bad Request");
				outToClient.writeBytes("HTTP/1.0 408 Request Timeout");
				outToClient.flush();
				
				//Each of these, from this and the future ones all put the thread to sleep after the flush for 500 milliseconds or half a second is done to ensure that all of the data has gone through. 
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException e1) {					
					
					System.err.println("InterruptedException: Waiting time interrupted.");
				}
				
				closeConnections();
				return;
				
			} catch (IOException e1) {	
				System.err.println("IO Exception: Error writing messages.");
			}
			
		}catch(Exception e){ //will specify exceptions later
			//Exception!
		}
		
		
		if(command.equals("POST") && (clheader == false)){
			try {
				//System.err.println("408 Bad Request");
				outToClient.writeBytes("HTTP/1.0 411 Length Required");
				outToClient.flush();
				
				//Each of these, from this and the future ones all put the thread to sleep after the flush for 500 milliseconds or half a second is done to ensure that all of the data has gone through. 
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException e1) {					
					
					System.err.println("InterruptedException: Waiting time interrupted.");
				}
				
				closeConnections();
				return;
				
			} catch (IOException e1) {	
				System.err.println("IO Exception: Error writing messages.");
			}
		}else if(command.equals("POST") && (ctheader == false)){
			try {
				//System.err.println("408 Bad Request");
				outToClient.writeBytes("HTTP/1.0 500 Internal Server Error");
				outToClient.flush();
				
				//Each of these, from this and the future ones all put the thread to sleep after the flush for 500 milliseconds or half a second is done to ensure that all of the data has gone through. 
				try {
					Thread.currentThread().sleep(500);
				} catch (InterruptedException e1) {					
					
					System.err.println("InterruptedException: Waiting time interrupted.");
				}
				
				closeConnections();
				return;
				
			} catch (IOException e1) {	
				System.err.println("IO Exception: Error writing messages.");
			}
		}
		
		/*if(postString.length() >= postContentLength){
			postString = postString.substring(0, postContentLength-1);
		}*/
		
		
		//Creates a new string that is the Content-Length
		if("".equals(postString) == false){
			try{
			
				byte[] tempBytes = postString.getBytes("UTF-8");
				byte[] tempBytes2 = new byte[postContentLength];
				for(int w = 0; w < postContentLength; w++){
					tempBytes2[w] = tempBytes[w];
				}
				
				postString = new String(tempBytes2, "US-ASCII");
				
				
			}catch(UnsupportedEncodingException M){
				//EXCEPTION! wat do?
			}
		}
		
		
			
			//This path represents the file that the client wants us to retrieve. 
			Path p1 = Paths.get("."+path);
			
			//If the file exists is false and doesn't exist is false, then this might mean that we don't have appropriate access
			//permissions to retrieve the file and that this is an internal error from our end, where we just can't actually retrieve the file.
			if(Files.exists(p1) == false && Files.notExists(p1) == false){
				try {
					
					outToClient.writeBytes("HTTP/1.0 500 Internal Error");
					outToClient.flush();
					
				} catch (IOException e) {					
					
					System.err.println("IOh Exception: Error writing messages.");
				}
				
				
			//If file not exists is true, then the file actually doesn't exist and this results in the 404 not found response.	
			}else if(Files.notExists(p1)){
				
				try {
					outToClient.writeBytes("HTTP/1.0 404 Not Found");
					outToClient.flush();
				} catch (IOException e) {					
					
					System.err.println("IO jjException: Error writing messages.");
				}
				
			//Finally, if the file does exist, then all of the file contents are read as bytes, and the both the http okay response
				//as well as the file contents are sent to the user. 
			
			}else{
				//This is done in order to check that the file is readable, so create a File object. 
				File fileread = new File(p1.toString());
				
				if(command.equals("POST") && fileread.isFile() ){
					if(fileread.canExecute()==false){
						try{
							outToClient.writeBytes("HTTP/1.0 403 Forbidden");
							outToClient.flush();
						}catch(IOException e){
							
						}
						
					}else{
						
						String pStringDecoded = "";
						try {
							pStringDecoded = URLDecoder.decode(postString, "UTF-8");
							
							
							CONTENT_LENGTH = pStringDecoded.getBytes("UTF-8").length+"";
							
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						ArrayList<String> envip = new ArrayList<String>();
						if(CONTENT_LENGTH != null){
							envip.add("CONTENT_LENGTH="+CONTENT_LENGTH);
						}
						if(SCRIPT_NAME != null){
							envip.add("SCRIPT_NAME="+SCRIPT_NAME);
						}
						if(SERVER_NAME != null){
							envip.add("SERVER_NAME="+SERVER_NAME);
						}
						if(SERVER_PORT != null){
							envip.add("SERVER_PORT="+SERVER_PORT);
						}
						if(HTTP_FROM != null){
							envip.add("HTTP_FROM="+HTTP_FROM);
						}
						if(HTTP_USER_AGENT != null){
							envip.add("HTTP_USER_AGENT="+HTTP_USER_AGENT);								
						}
						
						String[] envp = envip.toArray(new String[envip.size()]);
						
						FileTime lmdate = null;
						SimpleDateFormat form = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
	                    form.setTimeZone(TimeZone.getTimeZone("GMT"));
	                    long currtime = System.currentTimeMillis();
	                    currtime+= 604800000;
						try {
							lmdate = Files.getLastModifiedTime(p1);
						} catch (IOException e) {	
							
							System.err.println("IOException: Error in getting last Modified time");
						}
						
						Process process = null;
						try {
							process = Runtime.getRuntime().exec(fileread.getAbsolutePath(), envp);
							
						} catch (IOException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						//envp = {"CONTENT_LENGTH=2","SERVER_NAME=127.0.0.1"
						//process.getInputStream() will give an inputstream, and getoutputstream will given an outputstream.output stream gives the stdinput of hte cgi script. 
						OutputStream stdin =  process.getOutputStream();
						try {
							
							//byte[] temp = pStringDecoded.getBytes("UTF-8");
							//for(int i =0; i<temp.length; i++){
							//	stdin.write(temp[i]);
							//}
							
							stdin.write(pStringDecoded.getBytes("UTF-8"));
							stdin.close();
							
						} catch (IOException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						InputStream stdout = process.getInputStream();
						int rawByte;
						int loopc = 0;
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						
						try {
							
							//System.out.println("rawByte "+rawByte);
							while((rawByte = stdout.read()) != -1){
								//System.out.println("IN WHILE LOOP");
								byte tempbyte = (byte) rawByte;
								baos.write(tempbyte);
								loopc++;
								
							}
							
							
							if(loopc == 0){
								
								try {
									//System.err.println("408 Bad Request");
									outToClient.writeBytes("HTTP/1.0 204 No Content");
									outToClient.flush();
									
									//Each of these, from this and the future ones all put the thread to sleep after the flush for 500 milliseconds or half a second is done to ensure that all of the data has gone through. 
									try {
										Thread.currentThread().sleep(500);
									} catch (InterruptedException e1) {					
										
										System.err.println("InterruptedException: Waiting time interrupted.");
									}
									
									closeConnections();
									return;
									
								} catch (IOException e1) {	
									System.err.println("IO Exception: Error writing messages.");
								}
								
							}
						} catch (IOException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						int balength = baos.size();
						try{
							outToClient.writeBytes("HTTP/1.0 200 OK"+"\r\n"+"Content-Type: "+"text/html"+"\r\n"+"Content-Length: "+balength+"\r\n"+"Last-Modified: "+form.format(lmdate.toMillis())+"\r\n"+"Content-Encoding: identity"+"\r\n"+"Allow: GET, POST, HEAD"+"\r\n"+"Expires: "+form.format(currtime)+"\r\n"+"\r\n");
							
							outToClient.flush();
						}catch(IOException e){
							System.err.println("BLUE");
						}
						
						try {
							Thread.currentThread().sleep(500);
						} catch (InterruptedException e1) {	
							
							System.err.println("InterruptedException: Waiting time interrupted.");
						}
						byte[] bytearray = baos.toByteArray();
					
						try{
							outToClient.write(bytearray);
							outToClient.flush();
						}catch(IOException e){
							System.err.println("IOException in wrjjiting messages");
						}
						
						//stdin.write() -- will allow you to write a byte to the stdout.
						//can do a while loop to read results from the cgi. int rawByte = -1;
						//while((rawByte = stdout.read()) !=-1){ (byte) rawByte}
						//put the rawBytes into a list of bytes and in the end yu calculate the length of the byte list and treat it as the content length of teh response.
						//Then you append the bytes as the payload of your response. 
						//bytebuilder
					}
				}
				//If the file is readable then the following portion of code is executed and this will result in a 200 OK or 304 Not Modified depending on the initial HTTP request. 
				else if(fileread.isFile() && fileread.canRead()){
					byte[] fileContents = null;
					
					try {
						fileContents = Files.readAllBytes(p1);
					} catch (IOException e) {	
						
						System.err.println("IOException: Error in reading filecontents as bytes");
					}
					
                    FileTime lmdate = null;
                    
					try {
						lmdate = Files.getLastModifiedTime(p1);
					} catch (IOException e) {	
						
						System.err.println("IOException: Error in getting last Modified time");
					}
					
					//Sets format for Last Modified, including the way the string should be displayed as well as the timezone for the date.
                    SimpleDateFormat form = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
                    form.setTimeZone(TimeZone.getTimeZone("GMT"));
                    
                    
                    //Checks for 304 Not Modified
                    if(LM && !command.equals("HEAD")){
                    	
                    	//Converts Last Modified date into Milliseconds so that it can be compared
                    	long lastMod = lmdate.toMillis();
                
                    	if(lastMod <= modSince){
                    		
                			long currtime = System.currentTimeMillis();
                			//This adds 7 days to the current time in order to create the expires in future portion of the request.
                            currtime+= 604800000;
							
							
							try{
								outToClient.writeBytes("HTTP/1.0 304 Not Modified"+"\r\n"+"Expires: "+form.format(currtime)+"\r\n");
								outToClient.flush();
							}catch(IOException e){
								System.err.println("IOException in wrjjiting messages");
							}
                			try {
                				Thread.currentThread().sleep(500);
                			} catch (InterruptedException e1) {               				
                				
                				System.err.println("InterruptedException: Waiting time interrupted.");
                			}
                			//At this point, all connections will be closed, and the socket will also be closed. 
                			closeConnections();
                			return;
                    	}                    	
                    }                   
                    
                    //Setup for Expiration date
                    long currtime = System.currentTimeMillis();
                    currtime+= 604800000;
                    String contenttype ="";
					try {
						contenttype = Files.probeContentType(p1);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
                   
                    if(!"text/html".equals(contenttype) && !"text/plain".equals(contenttype) && !"image/gif".equals(contenttype) && !"image/jpeg".equals(contenttype) && !"image/png".equals(contenttype) && !"application/pdf".equals(contenttype) && !"application/z-gzip".equals(contenttype) && !"application/zip".equals(contenttype)){
                    	contenttype = "application/octet-stream";
                    }
                    //Everything is OK, returns the 200 OK message, using the JAVA equivalent of <CRLF> in order to separate each line.
					
					
					try{
						outToClient.writeBytes("HTTP/1.0 200 OK"+"\r\n"+"Content-Type: "+contenttype+"\r\n"+"Content-Length: "+Files.size(p1)+"\r\n"+"Last-Modified: "+form.format(lmdate.toMillis())+"\r\n"+"Content-Encoding: identity"+"\r\n"+"Allow: GET, POST, HEAD"+"\r\n"+"Expires: "+form.format(currtime)+"\r\n"+"\r\n");
						outToClient.flush();
					}catch(IOException e){
						System.err.println("IOE1xception in writing messages");
					}
                   //If the request is not HEAD, so if it is either GET or POST, then the filecontents are also displayed. HOWEVER, if the request is Head, then only the header is returned.
					if(command.equals("HEAD") == false){
						try{
							outToClient.write(fileContents);
							outToClient.flush();
						}catch(IOException e){
							System.err.println("IOException in wrjjiting messages");
						}
						
					}	
					
				}else{
					//This is returned if the file is unreadable as we don't have the appropriate permissions.
					try{
						outToClient.writeBytes("HTTP/1.0 403 Forbidden");
						outToClient.flush();
					}catch(IOException e){
						System.out.println("sdkfhkdf");
					}
					
				}				
			}
			
			try {
				Thread.currentThread().sleep(500);
			} catch (InterruptedException e1) {	
				
				System.err.println("InterruptedException: Waiting time interrupted.");
			}
			
			//At this point, all connections will be closed, and the socket will also be closed. 
			closeConnections();
			return;
			
		
		}
		
	
	//This method was written so as not to have to repeatedly write out all of the closing statements at each point when we may wish to 
	//exit the program. This method is called whenever a response is sent to the user and the socket and appropriate connections all need to be closed. 
	public void closeConnections(){
		try{
			
			outToClient.close();
			clientMessage.close();
			connectionSocket.close();
			return;
			
		}catch(IOException e){
			System.err.println("IOException in closing the streams/socket");
		}
	}
}


