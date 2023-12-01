import java.net.*;
import java.io.*;

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

        while (!EOT) {
            socket.receive(receivedPacket);
            
            // Process received data
            receivedString = new String(receivedPacket.getData(), 0, receivedPacket.getLength());
            if (receivedString.equals("!EOT!")) {
                EOT = true;
                // STOP and WAIT
                ackSend(socket, SERVER_HOST, receivedPacket.getPort());
            } else {
                System.out.println("Received data from server: " + receivedString);
                // STOP and WAIT
                ackSend(socket, SERVER_HOST, receivedPacket.getPort());
            }
        }

    }

    private static void ackSend(DatagramSocket socket, String serverHost, int serverPort) throws IOException {
        // Simulate sending ACK
        int ackNumber = sequence_number;
        sequence_number = (sequence_number + 1) % 2; // incr sequence num

        byte[] ackData = Integer.toString(ackNumber).getBytes();
        InetAddress serverAddress = InetAddress.getByName(serverHost);
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverAddress, serverPort);
        socket.send(ackPacket);
    }

    private static byte[] prepareData(String data) {
        // DatagramPackets do not natively support sequence numbers since UDP does not guarantee in-order delivery, so the sequence numbers are padded onto the data and parsed by the server architecture
        String padded = sequence_number + data;

        return padded.getBytes();
    }
}
