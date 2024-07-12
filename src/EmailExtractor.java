import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
* Metodologias Utilizadas:

* SwingWorker e Swing:
  - Utilizados para realizar operações longas em threads separadas, garantindo que a interface gráfica (GUI) não seja travada durante o
    processamento de extração de emails.
* Jsoup:
   - Biblioteca Java para análise e manipulação de documentos HTML. Utilizada para fazer requisições HTTP, obter o conteúdo HTML das
     páginas web e extrair emails através de expressões regulares.
* Concorrência com Filas:
  - Implementação de uma fila (Queue) para gerenciar as URLs a serem processadas, garantindo que todas as páginas do mesmo domínio
    sejam exploradas em busca de emails.
* Regex (Expressões Regulares):
  - Utilizadas para identificar padrões de emails no conteúdo HTML das páginas.
    A expressão regular "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}" foi empregada para esse fim.
* Interface Gráfica (GUI):
  - Desenvolvida com o uso de componentes Swing (JFrame, JTextField, JTextArea, JButton) para fornecer uma interface intuitiva ao
    usuário, permitindo inserção de URL, visualização de links e emails

* Além de botões para controle de execução e limpeza dos dados.

* Processo de Seleção de Links para Análise de Emails:
   - Busca em Profundidade (DFS): O sistema utiliza uma abordagem de busca em largura (Queue) para explorar as páginas web a
     partir de uma URL inicial fornecida pelo usuário.

   - Filtragem por Domínio: A cada página analisada, são coletados todos os links (<a href>) que pertencem ao mesmo domínio
     da URL inicial. Isso garante que apenas links relevantes sejam adicionados à fila para análise subsequente.

   - Prevenção de Ciclos: A lista visited é utilizada para armazenar URLs já visitadas, evitando que páginas sejam processadas
     mais de uma vez e prevenindo ciclos infinitos na busca.

   - Extração de Emails: Para cada página processada, o sistema busca por padrões de emails utilizando expressões regulares,
     capturando todas as ocorrências válidas e armazenando em um conjunto (HashSet) para evitar duplicatas.

   - Atualização da Interface: A interface gráfica é atualizada em tempo real usando o método publish do SwingWorker, exibindo os links sendo processados e os emails encontrados na área designada, mantendo o usuário informado do progresso da operação.
* * */

public class EmailExtractor {

    private JFrame frame;
    private JTextField textField;
    private JTextArea textArea_emails;
    private JTextArea textArea_links;
    private JButton buttonExtract;
    private JButton buttonStop;
    private JButton buttonClear;
    private JTextField filterField;

    private Set<String> emails = new HashSet<>();
    private Set<String> visited = new HashSet<>();
    private volatile boolean running = false;

    private EmailExtractionWorker extractionWorker;

    public EmailExtractor() {
        frame = new JFrame("Email ExtractorV1.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Enter URL:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        textField = new JTextField(30);
        panel.add(textField, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        buttonExtract = new JButton("Extract");
        panel.add(buttonExtract, gbc);

        gbc.gridx = 4;
        buttonStop = new JButton("Stop");
        buttonStop.setEnabled(false);
        panel.add(buttonStop, gbc);

        gbc.gridx = 5;
        buttonClear = new JButton("Clear");
        panel.add(buttonClear, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Filter Emails:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        filterField = new JTextField(30);
        panel.add(filterField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        textArea_emails = new JTextArea(20, 40);
        textArea_emails.setFont(new Font("Arial", Font.PLAIN, 14));
        textArea_emails.setLineWrap(true);
        textArea_emails.setWrapStyleWord(true);
        JScrollPane scrollPane_emails = new JScrollPane(textArea_emails);
        panel.add(scrollPane_emails, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 3;
        textArea_links = new JTextArea(20, 40);
        textArea_links.setFont(new Font("Arial", Font.PLAIN, 14));
        textArea_links.setLineWrap(true);
        textArea_links.setWrapStyleWord(true);
        JScrollPane scrollPane_links = new JScrollPane(textArea_links);
        panel.add(scrollPane_links, gbc);

        frame.add(panel);
        frame.setVisible(true);

        buttonExtract.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startEmailExtraction(textField.getText());
            }
        });

        buttonStop.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopEmailExtraction();
            }
        });

        buttonClear.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearFields();
            }
        });

        filterField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filterEmails();
            }
        });
    }

    private void startEmailExtraction(String startUrl) {
        if (!running) {
            running = true;
            extractionWorker = new EmailExtractionWorker(startUrl);
            extractionWorker.execute();
            buttonStop.setEnabled(true);
            buttonExtract.setEnabled(false);
        } else {
            JOptionPane.showMessageDialog(frame, "Extraction already in progress.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void stopEmailExtraction() {
        if (extractionWorker != null) {
            extractionWorker.stopExtraction();
            running = false;
            buttonStop.setEnabled(false); // Desabilitar o botão de Stop após a extração parar
            buttonExtract.setEnabled(true);
        }
    }

    private void clearFields() {
        textField.setText("");
        textArea_emails.setText("");
        textArea_links.setText("");
        filterField.setText("");
        emails.clear();
        visited.clear();
    }

    private void filterEmails() {
        String filterText = filterField.getText().trim().toLowerCase();
        textArea_emails.setText("");

        if (filterText.isEmpty()) {
            for (String email : emails) {
                textArea_emails.append(email + "\n");
            }
        } else {
            for (String email : emails) {
                if (email.toLowerCase().contains(filterText)) {
                    textArea_emails.append(email + "\n");
                }
            }
        }
    }

    private class EmailExtractionWorker extends SwingWorker<Void, Object> {

        private final String startUrl;
        private final Queue<String> queue = new LinkedList<>();
        private final Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        private volatile boolean stopped = false;

        public EmailExtractionWorker(String startUrl) {
            this.startUrl = startUrl;
            queue.add(startUrl);
        }

        @Override
        protected Void doInBackground() {
            while (!queue.isEmpty() && !stopped) {
                String url = queue.poll();
                publish(new LinkData(url));

                if (!visited.contains(url)) {
                    visited.add(url);

                    try {
                        Document doc = Jsoup.connect(url).get();
                        String html = doc.html();

                        Matcher matcher = emailPattern.matcher(html);
                        while (matcher.find()) {
                            String email = matcher.group();
                            if (emails.add(email)) {
                                publish(new EmailData(email));
                            }
                        }

                        Elements links = doc.select("a[href]");
                        for (Element link : links) {
                            String absUrl = link.absUrl("href");
                            if (isSameDomain(absUrl, startUrl) && !visited.contains(absUrl)) {
                                queue.add(absUrl);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void process(java.util.List<Object> chunks) {
            for (Object chunk : chunks) {
                if (chunk instanceof LinkData) {
                    LinkData linkData = (LinkData) chunk;
                    textArea_links.append(linkData.getUrl() + "\n");
                    textArea_links.setCaretPosition(textArea_links.getDocument().getLength());
                } else if (chunk instanceof EmailData) {
                    EmailData emailData = (EmailData) chunk;
                    textArea_emails.append(emailData.getEmail() + "\n");
                    textArea_emails.setCaretPosition(textArea_emails.getDocument().getLength());
                }
            }
        }

        @Override
        protected void done() {
            running = false;
            buttonStop.setEnabled(false); // Desabilitar o botão de Stop ao término da extração
            buttonExtract.setEnabled(true);
            textArea_emails.append("Extraction complete.\n");
        }

        public void stopExtraction() {
            stopped = true;
        }
    }

    private boolean isSameDomain(String url1, String url2) {
        try {
            URI uri1 = new URI(url1);
            URI uri2 = new URI(url2);
            return uri1.getHost().equalsIgnoreCase(uri2.getHost());
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new EmailExtractor());
    }

    private class EmailData {
        private String email;

        public EmailData(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }

    private class LinkData {
        private String url;

        public LinkData(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }
}
