import java.net.*;
import java.util.Random;
import java.io.*;


/* Client
 * 
 * 3.1
 * The client C asks the user to enter a string s with the name of a Web server W in the form: www.name.suf (for example, s=www.towson.edu). It then sends a message to S that has the bytes of the string s as payload.
 */
public class Addowclient {
    // port 11122 and localhost per instructions
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 11122;
    private static int sequence_number = 0;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            // Get Web server name from the user
            System.out.println("Enter Web server (www.name.suf):");
            String webServerString = reader.readLine();

            // Send the Web server name to the server
            sendData(socket, SERVER_HOST, SERVER_PORT, webServerString);

            // Receive data from the server
            receiveData(socket);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendData(DatagramSocket socket, String serverHost, int serverPort, String data) throws IOException {
        // prepare the String data for transfer
        byte[] sendData = prepareData(data);

        InetAddress serverAddress = InetAddress.getByName(serverHost);
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        socket.send(sendPacket);
    }

    // TODO: need to recieve more data after the ACK send
    private static void receiveData(DatagramSocket socket) throws IOException {
        byte[] receivedData = new byte[1024];
        String receivedString;
        DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
        boolean EOT = false;
        int currentSequenceNumber = -1;
        int receivedSequenceNumber;

        while (!EOT) {
            socket.receive(receivedPacket);
            
            // Process received data
            receivedString = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
            receivedSequenceNumber = Integer.parseInt(receivedString.substring(0,1));
            String payload = receivedString.substring(1);

            if (currentSequenceNumber == receivedSequenceNumber) {
                System.out.println("\nReceived duplicate data from server!\n");
                // ignore the frame and resend the ack [increment so the prev ack is sent]
                sequence_number = (sequence_number + 1) % 2;

            } else if (payload.equals("!EOT!")) {
                EOT = true;
                System.out.println("Received EOT from server, ending.");

            } else {
                System.out.println("Received data from server: " + payload);
            }
            
            // STOP and WAIT
            ackSend(socket, SERVER_HOST, receivedPacket.getPort());
        }

    }

    private static void ackSend(DatagramSocket socket, String serverHost, int serverPort) throws IOException {
        // Simulate sending ACK, there is a ~10% error chance for testing purposes
        int ackNumber = sequence_number;
        sequence_number = (sequence_number + 1) % 2; // incr sequence num

        // random error sim
        Random rand = new Random();
        if(rand.nextInt(100) <= 10) {
            sequence_number = 3;
        }

        byte[] ackData = Integer.toString(ackNumber).getBytes();
        InetAddress serverAddress = InetAddress.getByName(serverHost);
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverAddress, serverPort);

        // random response loss sim (cause timeout)
        if(rand.nextInt(100) >= 10) {
            socket.send(ackPacket);
        }
    }

    private static byte[] prepareData(String data) {
        // DatagramPackets do not natively support sequence numbers since UDP does not guarantee in-order delivery, so the sequence numbers are padded onto the data and parsed by the server architecture
        String padded = sequence_number + data;

        return padded.getBytes();
    }
}
