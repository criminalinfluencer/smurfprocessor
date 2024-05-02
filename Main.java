import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.UUID;
import java.util.regex.*;

public class FileProcessor {

    private static List<String> removeBlankLines(List<String> lines) {
        List<String> nonBlankLines = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                nonBlankLines.add(line);
            }
        }
        return nonBlankLines;
    }

    private static SkinDetails fetchSkinDetails(String id) {
        String url = "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/en_gb/v1/skins.json";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            Pattern pattern = Pattern.compile("\"" + id + "\":\\s*\\{([^}]+)\\}");
            Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                String skinDetails = matcher.group(1);
                String name = extractJsonValue(skinDetails, "name");
                String rarity = extractJsonValue(skinDetails, "rarity");
                return new SkinDetails(name, rarity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String adjustStrings(String line) {
        String[] changes = {
                " (Permanent):", "(Shard)", "None", "Epic", "Legendary",
                "Ultimate", "Mythic", "PROJECT:", "Pentakill III:", "(kEPIC) (DEFAULT)",
                "(kLEGENDARY) (DEFAULT)", "(kULTIMATE) (DEFAULT)", "(kMYTHIC) (DEFAULT)",
                "(kDEFAULT) (DEFAULT)"
        };
        String[] replacements = {
                "", "", "DEFAULT", "EPIC", "LEGENDARY", "ULTIMATE",
                "MYTHIC", "PROJECT-", "Pentakill III-", "(EPIC)", "(LEGENDARY)",
                "(ULTIMATE)", "(MYTHIC)", "(DEFAULT)"
        };

        for (int i = 0; i < changes.length; i++) {
            line = line.replace(changes[i], replacements[i]);
        }
        return line;
    }

    public static String processLine(String line1, List<String> file2Lines) {
        String user = line1.split(":")[0].trim();
        String line2 = null;

        for (String line : file2Lines) {
            if (line.contains(user)) {
                line2 = line;
                break;
            }
        }

        String combinedLine = line1;
        if (line2 != null) {
            String[] parts1 = line1.split(":");
            String[] parts2 = line2.split(":");

            String skins = String.join(":", parts1, 2, parts1.length - 3);
            String ea = parts1[parts1.length - 3].trim();
            String el = parts1[parts1.length - 2].trim();
            String tag = parts1[parts1.length - 1].trim();

            String password2 = parts2[1].trim();
            String server = parts2[2].trim();
            String email = parts2[3].trim();
            String nascimento = parts2[4].trim();
            String criacao = parts2[5].trim();
            String pais = parts2[6].trim();

            combinedLine = String.format(
                "%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s",
                user, password2, server, email, nascimento, criacao, pais, skins, ea, el, tag
            );
        } else {
            combinedLine += ":Brasil";
        }

        if (combinedLine.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = Pattern.compile("CHAMPION_SKIN_RENTAL_(\\d+)").matcher(combinedLine);
        while (matcher.find()) {
            String id = matcher.group(1);
            SkinDetails skinDetails = fetchSkinDetails(id);
            if (skinDetails != null) {
                combinedLine = combinedLine.replace(
                        String.format("CHAMPION_SKIN_RENTAL_%s", id),
                        String.format("%s (%s)", skinDetails.getName(), skinDetails.getRarity())
                );
            }
        }

        combinedLine = adjustStrings(combinedLine);

        return combinedLine;
    }

    public static void processFiles(String file1Path, String file2Path, int threadCount, LogCallback logCallback) {
        UUID outputId = UUID.randomUUID();
        String outputPath = String.format("output_%s.txt", outputId.toString());

        try (BufferedReader br1 = new BufferedReader(new FileReader(file1Path));
             BufferedReader br2 = new BufferedReader(new FileReader(file2Path))) {

            List<String> file1Lines = removeBlankLines(br1.lines().collect(Collectors.toList()));
            List<String> file2Lines = removeBlankLines(br2.lines().collect(Collectors.toList()));

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<String>> futures = new ArrayList<>();

            for (String line1 : file1Lines) {
                futures.add(executor.submit(() -> processLine(line1, file2Lines)));
            }

            List<String> outputLines = new ArrayList<>();
            for (Future<String> future : futures) {
                String result = future.get();
                if (result != null) {
                    outputLines.add(result);
                }
            }

            executor.shutdown();

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {
                for (String line : outputLines) {
                    bw.write(line + "\n");
                }
            }

            logCallback.log("Output saved to " + outputPath + "\n");
        } catch (Exception e) {
            e.printStackTrace();
            logCallback.log("Error processing files: " + e.getMessage());
        }
    }

    public interface LogCallback {
        void log(String message);
    }

    public static class SkinDetails {
        private final String name;
        private final String rarity;

        public SkinDetails(String name, String rarity) {
            this.name = name;
            this.rarity = rarity;
        }

        public String getName() {
            return name;
        }

        public String getRarity() {
            return rarity;
        }
    }
}

class GUI {

    public void start() {
        JFrame frame = new JFrame("Holf File Processor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel file1Label = new JLabel("File 1:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(file1Label, gbc);

        JTextField file1TextField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(file1TextField, gbc);

        JButton file1BrowseButton = new JButton("Browse");
        gbc.gridx = 2;
        gbc.gridy = 0;
        panel.add(file1BrowseButton, gbc);

        JLabel file2Label = new JLabel("File 2:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(file2Label, gbc);

        JTextField file2TextField = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(file2TextField, gbc);

        JButton file2BrowseButton = new JButton("Browse");
        gbc.gridx = 2;
        gbc.gridy = 1;
        panel.add(file2BrowseButton, gbc);

        JLabel threadsLabel = new JLabel("Threads:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(threadsLabel, gbc);

        JTextField threadsTextField = new JTextField(5);
        threadsTextField.setText("1");
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(threadsTextField, gbc);

        JTextArea logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        panel.add(scrollPane, gbc);

        JButton startButton = new JButton("Start");
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(startButton, gbc);

        JButton stopButton = new JButton("Stop");
        gbc.gridx = 1;
        gbc.gridy = 4;
        panel.add(stopButton, gbc);

        file1BrowseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                file1TextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        file2BrowseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                file2TextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        startButton.addActionListener(e -> {
            String file1Path = file1TextField.getText();
            String file2Path = file2TextField.getText();
            int threadCount;

            try {
                threadCount = Integer.parseInt(threadsTextField.getText());
            } catch (NumberFormatException ex) {
                logArea.append("Invalid number of threads.\n");
                return;
            }

            if (file1Path.isEmpty() || file2Path.isEmpty() || threadCount < 1) {
                logArea.append("Please select both files and set a valid number of threads.\n");
            } else {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> FileProcessor.processFiles(file1Path, file2Path, threadCount, logArea::append));
                executor.shutdown();
            }
        });

        frame.add(panel);
        frame.setVisible(true);
    }
}

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI();
            gui.start();
        });
    }
}
