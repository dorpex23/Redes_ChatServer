import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class Room{
	String name;
	int members;
	
	Room(String s){
		name = s;
		members = 1;
	}
	
	public void addMember(){
		this.members++;
	}
	
	public void removeMember(){
		this.members--;
	}
	
	public boolean isEmpty(){
		return (members == 0);
	}
}

public class ChatServer
{
	
  static LinkedList<String> nicknames;
  static LinkedList<Room> chatRooms;
  static private Selector selector;
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    nicknames = new LinkedList<String>();
    chatRooms = new LinkedList<Room>();
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
            SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );

          } else if ((key.readyOps() & SelectionKey.OP_READ) ==
            SelectionKey.OP_READ) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  String[] attr = new String[3];
					attr = (String[])sc.keyFor(selector).attachment();
					if(attr[1] != null){
						nicknames.remove(attr[0]);
						Room r = getRoom(chatRooms,attr[1]);
						r.removeMember();
						
						if(r.isEmpty()){
							chatRooms.remove(r);
							System.out.println("Chatroom " + r.name.replace("\n","").replace("\r","") + " is empty. Deleting it.");
						}
						
						if(attr[2].equals("inside"))
							broadcastMsg(sc,"LEFT " + attr[0].replace("\n","").replace("\r",""),attr);
						//sendMsg(sc,"BYE");
						System.out.println(attr[0].replace("\n","").replace("\r","") + "( " + sc.getRemoteAddress() + " ) disconnected ");
					}
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }

  static private boolean find(LinkedList<String> l, String s){
	  for(String str : l){
		if(s.equals(str))
			return true;
	  }
	  return false;
  }
 
  static private boolean findRoom(LinkedList<Room> l, String s){
	  for(Room str : l){
		if(s.equals(str.name))
			return true;
	  }
	  return false;
  }
  
  static private Room getRoom(LinkedList<Room> l, String s){
	  for(Room r : l){
		if(s.equals(r.name))
			return r;
	  }
	  return null;
  }
  
  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc ) throws IOException {
		// Read the message to the buffer
		buffer.clear();
		sc.read( buffer );
		buffer.flip();

		// If no data, close the connection
		if (buffer.limit()==0) {
			return false;
		}

		// Decode and print the message to stdout
		String message1 = decoder.decode(buffer).toString();
		
		String[] cmd_chain = message1.split("\n");
		
		
		for(String message : cmd_chain){
			//Get socket attachment
			//And attachment contains info regarding the nickname and joined room
			//The attachment is [Nick , Room, State]
			String[] attr = new String[3];
			if(sc.keyFor(selector).attachment() == null) {
				sc.keyFor(selector).attach(new String[3]);
			}else{
				attr = (String[])sc.keyFor(selector).attachment();
			}
			
			//Is it a command?
			if(message.charAt(0) == '/' && message.charAt(1) != '/'){
				String[] par = message.split(" ");
				String cmd = par[0];
				if(par.length < 2){
					//Either /leave, /bye or invalid command
					if(cmd.replace("\n","").replace("\r","").equals("/leave")){
						
						//Check if the user is registered
						if(attr[0] != null){
							//Check if he is in a chat room
							if(attr[2].equals("inside")){
								String currRoom = attr[1];
								
								sendMsg(sc,"OK");
								broadcastMsg(sc,"LEFT " + attr[0].replace("\n","").replace("\r",""),attr);
								
								System.out.println(attr[0].replace("\n","").replace("\r","") + "( " + sc.getRemoteAddress() + " ) left " + attr[1].replace("\n","").replace("\r",""));
								Room r = getRoom(chatRooms,attr[1]);
								r.removeMember();
								if(r.isEmpty()){
									chatRooms.remove(r);
									System.out.println("Chatroom " + r.name.replace("\n","").replace("\r","") + " is empty. Deleting it.");
								}
								attr[1] = null;
								attr[2] = "outside";
								sc.keyFor(selector).attach(attr);
							}else{
								//User isn't in a room
								sendMsg(sc,"ERROR");
								System.out.println("User outside of room tried to leave.");
							}
						}else{
							//User isn't registered
							sendMsg(sc,"ERROR");
							System.out.println("Unregistered user tried to leave.");
							
						}
						
						
					}else if(cmd.replace("\n","").replace("\r","").equals("/bye")){
						
						nicknames.remove(attr[0]);
						Room r = getRoom(chatRooms,attr[1]);
						r.removeMember();
						
						if(r.isEmpty()){
							chatRooms.remove(r);
							System.out.println("Chatroom " + r.name.replace("\n","").replace("\r","") + " is empty. Deleting it.");
						}
						
						broadcastMsg(sc,"LEFT " + attr[0].replace("\n","").replace("\r",""),attr);
						sendMsg(sc,"BYE");
						
						System.out.println(attr[0].replace("\n","").replace("\r","") + "( " + sc.getRemoteAddress() + " ) disconnected ");
						
						attr = new String[3];
						attr[2] = "init";
						sc.keyFor(selector).attach(attr);
					}
						
				}else{
					//It's either /join or /nick
					if(cmd.equals("/nick")){
						String nick = par[1];
						if(!find(nicknames,nick)){
							nicknames.add(nick);
							nicknames.remove(attr[0]);
							sendMsg(sc,"OK");
							if(attr[1] == null || attr[2].equals("init")){
								attr[2] = "outside";
							}else{
								//System.out.println("NEWNICK " + attr[0] + " " + nick);		
								broadcastMsg(sc,"NEWNICK " + attr[0].replace("\n","").replace("\r","") + " " + nick,attr);
							}
							attr[0] = nick;
							sc.keyFor(selector).attach(attr);
							System.out.println(sc.getRemoteAddress() + " registered as " + nick);
						}else{
							if(attr[0] != null){
								String aux = attr[0];
								aux = aux.replace("\n","").replace("\r","");
								System.out.println(aux + " tried to register as " + nick);
							}else{
								System.out.println(sc.getRemoteAddress() + " tried to register as " + nick);
							}
							sendMsg(sc,"ERROR");
						}
						
					}else if(cmd.equals("/join")) {
						
						//Does the user have a defined nickname?
						if(attr[0] != null){
							
							sendMsg(sc,"OK");
							
							String roomID = par[1];
							
							//Is there a room with such ID already creates?
							if(!findRoom(chatRooms,roomID)){
								Room r1 = new Room(roomID);
								chatRooms.add(r1);
								
								//Was the user previously on another channel?
								if(attr[1] != null){
									sendMsg(sc,"OK");
									broadcastMsg(sc,"LEFT " + attr[0].replace("\n","").replace("\r",""),attr);
									System.out.println(attr[0].replace("\n","").replace("\r","") + "( " + sc.getRemoteAddress() + " ) left " + attr[1].replace("\n","").replace("\r",""));
									Room r = getRoom(chatRooms,attr[1]);
									r.removeMember();
									if(r.isEmpty()){
										chatRooms.remove(r);
										System.out.println("Chatroom " + r.name.replace("\n","").replace("\r","") + " is empty. Deleting it.");
									}
								}
								//chatRooms.add(new Room(roomID));
								attr[1] = roomID;
								attr[2] = "inside";
								sc.keyFor(selector).attach(attr);
								System.out.println(attr[0].replace("\n","").replace("\r","") + "( " + sc.getRemoteAddress() + " ) created chatroom " + roomID);
								 
							
							//The user is joining a pre-existing room
							}else{
								
								if(attr[1] != null){
									broadcastMsg(sc,"LEFT " + attr[0].replace("\n","").replace("\r",""),attr);
								}
								Room r = getRoom(chatRooms,roomID);
								r.addMember();
								attr[1] = roomID;
								attr[2] = "inside";
								sc.keyFor(selector).attach(attr);
								System.out.println(attr[0].replace("\n","").replace("\r","") + "( " + sc.getRemoteAddress() + " ) joined chatroom " + roomID);
							}
							
							broadcastMsg(sc,"JOINED " + attr[0].replace("\n","").replace("\r",""),attr);
						}else{
							//User has to register a nickname first
							System.out.println(sc.getRemoteAddress() + "  tried joined a chatroom without registering");
							sendMsg(sc,"ERROR");
						}
						
						
					}else if(cmd.equals("/priv")){
						//Send private message to user
						if(attr != null){
							if(attr[0] != null && !attr[2].equals("init")){
									
								boolean sent = sendPrivate(sc,"PRIVATEMSG " + attr[0].replace("\n","").replace("\r","") + "  " + message.substring(par[0].length() + par[1].length() + 2).replace("\n","").replace("\r",""),attr,par[1]);
								if(sent){
									sendMsg(sc,"OK");
								}else{
									sendMsg(sc,"ERROR");
								}
							}else{
								sendMsg(sc,"ERROR");
							}						
						}else{
							sendMsg(sc,"ERROR");
						}
						
						
					}else{
						//Invalid command
						System.out.println("Invalid command or syntax");
						sendMsg(sc,"ERROR");
					}
					
				}
				
				
			}else{
				if(message.charAt(1) == '/')
					message = message.substring(1);
				if(attr[1] != null && attr[2].equals("inside")){
					broadcastMsg(sc,"MESSAGE " + attr[0].replace("\n","").replace("\r","") + " " + message,attr);
				}else{
					sendMsg(sc,"ERROR");
				}
				
			}
		}
		//System.out.println("RECEIVED: "+ message);
		buffer.flip();
		
		buffer.clear();

		return true;
	}
	
	static private void sendMsg(SocketChannel s, String msg) throws IOException{
		msg = msg + "\n";
		s.write(ByteBuffer.wrap(msg.getBytes(charset)));		
	}
	
	static private void broadcastMsg(SocketChannel s,String msg, String[] attr) throws IOException{
    
		msg = msg + "\n";

		ByteBuffer auxBuffer = ByteBuffer.allocate( 16384 );
		auxBuffer = ByteBuffer.wrap(msg.getBytes(charset));

		selector.wakeup();

		Set<SelectionKey> keys = selector.keys();
		Iterator<SelectionKey> it = keys.iterator();
		while (it.hasNext()) {
			SelectionKey key = it.next();
			String[] auxAttr = (String[])key.attachment();
			if(auxAttr == null)
				continue;
			if(auxAttr[1] == null)
				continue;
			if(!auxAttr[1].equals(attr[1]))
				continue;
			if(auxAttr[0].equals(attr[0]))
				continue;
			if(!auxAttr[2].equals("inside"))
				continue;
			
			SocketChannel scAux = (SocketChannel)key.channel();
			
			while(auxBuffer.hasRemaining())
				scAux.write(auxBuffer);
			auxBuffer.rewind();
		}
	}
	static private boolean sendPrivate(SocketChannel s,String msg, String[] attr,String other) throws IOException{
    
    msg = msg + "\n";
    
    ByteBuffer auxBuffer = ByteBuffer.allocate( 16384 );
	auxBuffer = ByteBuffer.wrap(msg.getBytes(charset));

    selector.wakeup();
    
	Set<SelectionKey> keys = selector.keys();
	Iterator<SelectionKey> it = keys.iterator();
	while (it.hasNext()) {
		SelectionKey key = it.next();
		String[] auxAttr = (String[])key.attachment();
		if(auxAttr == null)
			continue;
		if(auxAttr[0] == null)
			continue;
		if(auxAttr[0].equals(attr[0]))
			continue;
		
		if(!auxAttr[0].replace("\n","").replace("\r","").equals(other))
			continue;
		SocketChannel scAux = (SocketChannel)key.channel();
		
        while(auxBuffer.hasRemaining())
			scAux.write(auxBuffer);
		auxBuffer.rewind();
		return true;
	}
	return false;
  
	}
}
