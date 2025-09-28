import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class Middleware {

    private Selector _EventMng;
    private ServerSocketChannel _MainSocket;
    private boolean running = true;
    private IMailBox _Box;
    private BiMap<String,SocketChannel> _SocketTable;
    private final int  BufferSize = 1024;
    private PrintStream _Serverlog;

    public Middleware(String ip, int port,PrintStream serverlog) throws IOException {

        _EventMng = Selector.open();
        _Serverlog = serverlog;

        _Box = new MailBox();

        _MainSocket = ServerSocketChannel.open();
        _MainSocket.bind(new InetSocketAddress(ip, port));

        _SocketTable = HashBiMap.create();

        _MainSocket.configureBlocking(false);

        _MainSocket.register(_EventMng, SelectionKey.OP_ACCEPT);

        _Serverlog.println("Server is up on " + ip + ":" + port);
    }

    public void Start() throws IOException {

        while (running) {

            _EventMng.select();

            Set<SelectionKey> Event = _EventMng.selectedKeys();
            Iterator<SelectionKey> Iterator = Event.iterator();

            while (Iterator.hasNext()) {

                SelectionKey key = Iterator.next();
                Iterator.remove();

                if (key.isAcceptable()) {
                    AcceptConnection(key);
                } else if (key.isReadable()) {
                    ReadMessage(key);
                }
            }
        }
    }

    private void AcceptConnection(SelectionKey key) throws IOException {

        ServerSocketChannel Sock = (ServerSocketChannel) key.channel();

        SocketChannel Client = Sock.accept();

        Client.configureBlocking(false);
        Client.register(_EventMng, SelectionKey.OP_READ);


        _Serverlog.println("New Client Connected: " + Client.getRemoteAddress());
    }

    private void ReadMessage(SelectionKey key) throws IOException {

        SocketChannel Client = (SocketChannel) key.channel();

        ByteBuffer Buffer = ByteBuffer.allocate(BufferSize);

        int BytesRead = 0;

        try {

            BytesRead = Client.read(Buffer);

        }catch(SocketException e)
         {

             _Serverlog.println("Client Disconnected: " + Client.getRemoteAddress());

            String UserName = _SocketTable.inverse().get(Client);

            if(UserName!=null) {

                _Box.RemoveUser(UserName);
               // _SocketTable.inverse().remove(client);
                _SocketTable.remove(UserName);
            }

            Client.close();
            key.cancel();

            return;
        }

        String Message = new String(Buffer.array(), 0, BytesRead).trim();

        _Serverlog.println("Recieveed By " + Client.getRemoteAddress() + ": " + Message);

        String Response = InterpretMessage(Message,Client);

        SendMessage(Client,Response);

    }



    private void SendMessage(SocketChannel client,String msg) throws IOException {

        ByteBuffer outBuffer = ByteBuffer.wrap(msg.getBytes());
        client.write(outBuffer);

    }

    private String InterpretMessage(String msg, SocketChannel Client) throws IOException {

        String[] arg = msg.split("\\s+");
        try {

            Command c = Command.fromString(arg[0].toLowerCase());

            switch (c) {

                case REGISTER: {
                    String UserId = arg[1];

                    if(_SocketTable.inverse().containsKey(Client))
                        return "You are Already Registered as "+_SocketTable.inverse().get(Client);

                    if (_Box.UserExist(UserId)) return "UserAlreadyExist";
                    _Box.CreateUserBox(UserId);
                    _SocketTable.put(UserId, Client);

                    return "User Successful Registered ";
                }
                case VIEW_MESSAGE: {

                    String UserId = _SocketTable.inverse().get(Client);

                    if (UserId != null) {
                        List<String> Messages = _Box.ViewUserMessage(UserId);

                        StringBuilder ret = new StringBuilder();
                        ret.append("\nMessages of ").append(UserId).append(":\n");


                        for(String s : Messages){

                        ret.append(s).append("\n");

                        }

                        return ret.toString();

                    } else return "You are not Registered";

                }
                case VIEW_USERS:{

                    StringBuilder ret = new StringBuilder();
                    ret.append("\nUsers Online").append(":\n");

                    for (SelectionKey key : _EventMng.keys()) {
                        if (key.channel() instanceof SocketChannel cl) {

                            ret.append("\nClient: ").append(cl.getRemoteAddress()).append(" status: ");

                            if(_SocketTable.inverse().containsKey(cl)){

                                ret.append("Registered as ").append(_SocketTable.inverse().get(cl));

                            }else ret.append("Not Registered");

                        }
                    }



                    return ret.toString();

                }

                case SEND_MESSAGE: {

                    String SenderId = _SocketTable.inverse().get(Client);
                    String RecieverId = arg[1];

                    if (SenderId != null && _Box.UserExist(RecieverId)) {

                        String Message = String.join(" ", Arrays.copyOfRange(arg, 2, arg.length));

                        _Box.AddMessage(SenderId, RecieverId, Message);

                        SocketChannel RecieverClient = _SocketTable.get(RecieverId);

                        SendMessage(RecieverClient, "New Message "+_Box.GetLastMessage(RecieverId));

                    return "Message Sent";

                    } else return SenderId == null ? "You are not Registered" : "User Doesn't Exist";

                }


            }
        }catch (ArrayIndexOutOfBoundsException e) {

            return "Error: Missing arguments for "+arg[0];

        }catch (IllegalArgumentException e){

            return  "Error: the Command: '"+arg[0]+"' Doesn't Exist";

        }
        return "Error: Uninterpretable message";

    }

    public void stop() throws IOException {
        running = false;
        _EventMng.close();
        _MainSocket.close();
        System.out.println("Server Successful Exit.");
    }


    public enum Command {

        REGISTER("register"),
        VIEW_MESSAGE("viewmessage"),
        VIEW_USERS("viewusers"),
        SEND_MESSAGE("sendmessage");

        private final String value;

        Command(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }

        public static Command fromString(String text) {
            for (Command s : Command.values()) {
                if (s.value.equalsIgnoreCase(text)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("Enum: " + text + "Doesn't Exist");
        }
    }



}
