import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Prueba de periféricos en Linux (riesgo R3, semana 3).
 * Imprime un ticket de prueba en una impresora térmica ESC/POS, la corta y abre el cajón de dinero
 * (el cajón se conecta a la impresora por el puerto RJ11 y se abre con un pulso que manda la impresora).
 *
 * Compilar:  javac PruebaPerifericos.java
 * USB:       java PruebaPerifericos /dev/usb/lp0
 * Red:       java PruebaPerifericos 192.168.1.50:9100
 * Sin impresora (genera archivo para revisar): java PruebaPerifericos ticket_prueba.bin
 *
 * En USB el usuario debe pertenecer al grupo lp:  sudo usermod -aG lp $USER  (y volver a iniciar sesión).
 */
public class PruebaPerifericos {

    // Comandos ESC/POS estándar (Epson y compatibles)
    static final byte[] INICIALIZAR = {0x1B, 0x40};                  // ESC @
    static final byte[] CODIGO_PAGINA_LATIN = {0x1B, 0x74, 16};      // ESC t 16 = WPC1252 (acentos y ñ)
    static final byte[] CENTRAR = {0x1B, 0x61, 1};                   // ESC a 1
    static final byte[] IZQUIERDA = {0x1B, 0x61, 0};                 // ESC a 0
    static final byte[] NEGRITA_SI = {0x1B, 0x45, 1};                // ESC E 1
    static final byte[] NEGRITA_NO = {0x1B, 0x45, 0};                // ESC E 0
    static final byte[] DOBLE_TAMANO = {0x1D, 0x21, 0x11};           // GS ! 0x11
    static final byte[] TAMANO_NORMAL = {0x1D, 0x21, 0x00};          // GS ! 0
    static final byte[] AVANZAR_Y_CORTAR = {0x1D, 0x56, 0x42, 0x03}; // GS V B 3 (avanza 3 y corte parcial)
    static final byte[] ABRIR_CAJON = {0x1B, 0x70, 0x00, 0x19, (byte) 0xFA}; // ESC p 0 25 250 (pin 2)

    static final Charset CP1252 = Charset.forName("windows-1252");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Uso: java PruebaPerifericos </dev/usb/lp0 | IP:9100 | archivo.bin>");
            System.exit(1);
        }
        byte[] ticket = construirTicket();
        long inicio = System.nanoTime();
        try (OutputStream salida = abrir(args[0])) {
            salida.write(ticket);
            salida.flush();
        }
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        System.out.printf("Enviados %d bytes a %s en %d ms.%n", ticket.length, args[0], ms);
        System.out.println("Revise: 1) se imprimen acentos y ñ, 2) el papel se corta, 3) el cajón abre.");
    }

    static byte[] construirTicket() throws IOException {
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        t.write(INICIALIZAR);
        t.write(CODIGO_PAGINA_LATIN);
        t.write(CENTRAR);
        t.write(DOBLE_TAMANO);
        texto(t, "VETERINARIA\n");
        t.write(TAMANO_NORMAL);
        texto(t, "Ticket de prueba · Sistema Veterinaria\n");
        texto(t, fecha + "\n");
        t.write(IZQUIERDA);
        texto(t, "--------------------------------\n");
        texto(t, renglon("1 Croquetas adulto 2 kg", "$389.00"));
        texto(t, renglon("2 Desparasitante", "$90.00"));
        texto(t, renglon("1 Consulta general", "$350.00"));
        texto(t, "--------------------------------\n");
        t.write(NEGRITA_SI);
        texto(t, renglon("TOTAL", "$829.00"));
        t.write(NEGRITA_NO);
        texto(t, "Acentos: á é í ó ú ñ Ñ ¿ ¡\n");
        t.write(CENTRAR);
        texto(t, "¡Gracias por su visita!\n");
        t.write(AVANZAR_Y_CORTAR);
        t.write(ABRIR_CAJON);
        return t.toByteArray();
    }

    /** Renglón de 32 columnas (papel de 58 mm); para 80 mm cambiar a 48. */
    static String renglon(String izquierda, String derecha) {
        int ancho = 32;
        int espacios = Math.max(1, ancho - izquierda.length() - derecha.length());
        return izquierda + " ".repeat(espacios) + derecha + "\n";
    }

    static void texto(ByteArrayOutputStream t, String s) throws IOException {
        t.write(s.getBytes(CP1252));
    }

    static OutputStream abrir(String destino) throws IOException {
        if (destino.matches("[0-9.]+:[0-9]+")) {
            String[] partes = destino.split(":");
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(partes[0], Integer.parseInt(partes[1])), 3000);
            return socket.getOutputStream(); // al cerrarse el stream se cierra el socket
        }
        return new FileOutputStream(destino);
    }
}
