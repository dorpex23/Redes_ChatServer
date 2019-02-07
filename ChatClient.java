import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.*;
import javax.swing.*;
import java.lang.Runtime;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();
    // --- Fim das variáveis relacionadas coma interface gráfica
	Socket clientSocket;
    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui



    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
		clientSocket = new Socket(server,port);
		

    }
    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor

      DataOutputStream outToServer =
         new DataOutputStream(this.clientSocket.getOutputStream());
         
      outToServer.write((new String(message + '\n')).getBytes(charset));
      if(message.charAt(0) == '/' && message.charAt(1) == '/'){
		  message = message.substring(1);
	  }
		printMessage("Tu: " + message + "\n");
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI

		while(true){
			BufferedReader inFromServer =
						new BufferedReader(new
							InputStreamReader(clientSocket.getInputStream()));
			String msg = inFromServer.readLine();
			if(msg == null){
				clientSocket.close();
				System.exit(0);
				return;
			}
            String[] tokens = msg.split(" ");
            System.out.println(msg);
            if(tokens[0].equals("MESSAGE")){
				printMessage(tokens[1] + ": " + msg.substring(8 + tokens[1].length()) + "\n");
			}else if(tokens[0].equals("NEWNICK")){
				printMessage(tokens[1] + " mudou de nome para " + tokens[2] + " .\n");
			}else if(tokens[0].equals("JOINED")){
				printMessage(tokens[1] + " juntou-se ao chat.\n");
			}else if(tokens[0].equals("PRIVATEMSG")){
				printMessage(tokens[1] + " mandou-lhe uma mensagem privada:" + msg.substring(11 + tokens[1].length()) + "\n");
			}else if(tokens[0].equals("LEFT")){
				printMessage(tokens[1] + " saiu da sala.\n");
			}else{
				printMessage(msg + "\n");
			}
		}

    }
    

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
    
        client.run();
    }

}
