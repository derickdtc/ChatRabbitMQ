package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class Chat {
    private static final String HOST = "34.229.195.16"; // ALTERAR
    private static final String USERNAME = "admin"; // ALTERAR
    private static final String PASSWORD = "password"; // ALTERAR
    private static final String VHOST = "/";

    private static Channel channel;
    private static String usuarioAtual;
    private static String destinatarioAtual = "";
    private static final Scanner scanner = new Scanner(System.in);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm");

    public static void main(String[] argv) throws Exception {
        setupConnection();
        pedirNomeUsuario();
        criarFilaUsuario();
        iniciarConsumidor();
        iniciarPromptEnvio();
    }

    private static void setupConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setVirtualHost(VHOST);

        Connection connection = factory.newConnection();
        channel = connection.createChannel();
    }

    private static void pedirNomeUsuario() {
        System.out.print("User: ");
        usuarioAtual = scanner.nextLine().trim();

        if (usuarioAtual.isEmpty()) {
            System.out.println("Nome de usuário não pode ser vazio!");
            pedirNomeUsuario();
        }
    }

    private static void criarFilaUsuario() throws IOException {
        // Cria a fila do usuário atual (ex: tarcisiorocha)
        channel.queueDeclare(usuarioAtual, false, false, false, null);
        System.out.println("Fila '" + usuarioAtual + "' criada.");
    }

    private static void iniciarConsumidor() {
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String mensagemCompleta = new String(body, "UTF-8");
                // Formato esperado: "remetente:texto"
                String[] partes = mensagemCompleta.split(":", 2);
                if (partes.length == 2) {
                    String remetente = partes[0];
                    String texto = partes[1];
                    String timestamp = sdf.format(new Date());
                    System.out.println("\n(" + timestamp + ") " + remetente + " diz: " + texto);
                } else {
                    // fallback
                    String timestamp = sdf.format(new Date());
                    System.out.println("\n(" + timestamp + ") " + mensagemCompleta);
                }
                exibirPrompt();
            }
        };

        try {
            channel.basicConsume(usuarioAtual, true, consumer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void iniciarPromptEnvio() {
        new Thread(() -> {
            exibirPrompt();
            while (true) {
                String entrada = scanner.nextLine().trim();

                if (entrada.isEmpty()) {
                    exibirPrompt();
                    continue;
                }

                if (entrada.startsWith("@")) {
                    String novoDestinatario = entrada.substring(1).trim();
                    if (!novoDestinatario.isEmpty()) {
                        destinatarioAtual = novoDestinatario;
                        // Verifica se a fila do destinatário existe (opcional, RabbitMQ aceita mesmo se não existir)
                        try {
                            channel.queueDeclarePassive(destinatarioAtual);
                        } catch (IOException e) {
                            System.out.println("Aviso: usuário '" + destinatarioAtual + "' pode não estar online.");
                        }
                    }
                } else if (!destinatarioAtual.isEmpty()) {
                    // Envia mensagem
                    String mensagem = usuarioAtual + ":" + entrada;
                    try {
                        channel.basicPublish("", destinatarioAtual, null, mensagem.getBytes("UTF-8"));
                    } catch (IOException e) {
                        System.out.println("Erro ao enviar mensagem para " + destinatarioAtual);
                    }
                } else {
                    System.out.println("Use @usuario para iniciar uma conversa.");
                }

                exibirPrompt();
            }
        }).start();
    }

    private static void exibirPrompt() {
        if (!destinatarioAtual.isEmpty()) {
            System.out.print("@" + destinatarioAtual + ">> ");
        } else {
            System.out.print(">> ");
        }
        System.out.flush();
    }
}