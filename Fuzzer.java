import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    private static final boolean DEBUG  = false;
    private static final boolean FUZZ_ALL = true;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789<>()[]{}|?\\ \"='\n\t\r";

    public static int non_zero_exits = 0;
    public static List<String> non_zero_inputs = new ArrayList<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        String seedInput = "<<6CTYPE\nmlk1<rtml\\lang \"in\"k1<riadk1</riadk1<M>dnk1</M>dnk1</rtmlk1";
        seedInput="<<>";
        runTestSuit(builder,seedInput);         //Used to narrow down the final reasons

        System.out.println();
        System.out.println();

        seedInput = HTML_SIMPLE;
        System.out.println("Validating SIMPLE_HTML:");
        runTestSuit(builder,seedInput);

        //Fuzzing only the simple HTML turned out to be the best way to pars the error causes from the data
        /*
        seedInput = HTML_STRUCTURE;
        System.out.println("Validating HTML Structure:");
        runTestSuit(builder,seedInput);

        seedInput = HEAD_SECTION.replaceAll("="," ");
        System.out.println("Validating Head Section:");
        runTestSuit(builder,seedInput);

        seedInput = CSS_STYLES;
        System.out.println("Validating CSS styles:");
        runTestSuit(builder,seedInput);
        */

        System.out.println("---------------------Bye---------------------");
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> {
                    try {
                        if (DEBUG)
                            System.out.print("Running Program with Input: " + input + " -> ");
                        Process process = builder.start();
                        OutputStream builderOut = process.getOutputStream();
                        builderOut.write(input.getBytes());
                        builderOut.flush();
                        builderOut.close();
                        int exitCode = process.waitFor();
                        if (DEBUG)
                            System.out.println(exitCode);
                        if (exitCode != 0) {
                            non_zero_exits++;
                            non_zero_inputs.add(input);
                            System.err.println("Command failed with exit code: " + exitCode +" | Used Input: "+input);
                        }
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Error running command: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }
        );
        System.out.println("---------------------------------------------");
        System.out.println("Found "+ non_zero_exits + " non_zero_exitcodes");
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String,List<String>>> mutators) {
        return mutators.stream().map(
                mutator -> mutator.apply(seedInput)
        ).flatMap(List::stream).toList();
    }

    public static String mutateRandomChars(String input) {
        Random random = new Random();
        int amount = 10;
        for (int i = 0; i < amount; i++) {
            int index = random.nextInt(CHARACTERS.length());
            input = input.replace(input.charAt(random.nextInt(input.length())),CHARACTERS.charAt(index));
        }

        return input;
    }

    public static List<String> genericMultiplexFuzz(Function<String, String> function,String input, int amount){
        List<String> list = new ArrayList<>();
        for(int i = 0; i<amount;i++){
            list.add(function.apply(input));
        }
        return list;
    }

    //Relic of the before-generic times
    public static List<String> mutateMultipleChars(int amount, String input) {
        List<String> result = new ArrayList<>();
        for(int i = 0; i<amount;i++)
            result.add(mutateRandomChars(input));
        return result;
    }

    public static void runTestSuit(ProcessBuilder builder, String seedInput){
        int exitCode = 0;
        try {
            Process process = builder.start();
            OutputStream builderOut = process.getOutputStream();
            builderOut.write(seedInput.getBytes());
            builderOut.flush();
            builderOut.close();
            exitCode = process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error running command: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
        if(exitCode==0||FUZZ_ALL){
            if(exitCode!=0)
                System.out.println("INVALID SEED: "+seedInput);
            runCommand(builder, seedInput, getMutatedInputs(seedInput, List.of(
                    input -> List.of(input.replace("<html", "a")), // this is just a placeholder, mutators should not only do hard-coded string replacement
                    input -> List.of(input.replace("<html", "")),
                    input -> genericMultiplexFuzz(Fuzzer::mutateRandomChars,input,10),
                    input -> List.of(input.toLowerCase()),
                    input -> List.of(input.toUpperCase()),
                    input -> List.of(input+"\n"+input),
                    input -> genericMultiplexFuzz(Fuzzer::deleteRandomCharacters,input,10),
                    input -> genericMultiplexFuzz(Fuzzer::addRandomCharacter, input, 10)
            )));
        }else{
            System.err.println("INVALID SEED...SKIPPING FUZZER");
        }
    }

    //Method AI generated and then edited; Fuzzing Idea by me
    public static String deleteRandomCharacters(String input) {
        int min = 0;
        int max = 10;   //Also tried with 16 32 64 and 128
        if (input == null || input.isEmpty() || min < 0 || max < min) {
            throw new IllegalArgumentException("Invalid input or range");
        }

        Random random = new Random();
        int numCharsToRemove = random.nextInt(max - min + 1) + min;

        // Ensure we do not remove more characters than the length of the string
        numCharsToRemove = Math.min(numCharsToRemove, input.length());

        StringBuilder result = new StringBuilder(input);

        for (int i = 0; i < numCharsToRemove; i++) {
            int indexToRemove = random.nextInt(result.length());
            result.deleteCharAt(indexToRemove);
        }

        return result.toString();
    }

    //Method AI generated and then edited; Fuzzing Idea by me
    public static String addRandomCharacter(String input) {
        int amount = 10; //Also tried with 16 32 64 and 128
        if (input == null) {
            throw new IllegalArgumentException("Input string cannot be null");
        }

        Random random = new Random();
        StringBuilder result = new StringBuilder(input);
        for(int i = 0; i<amount;i++){
            // Generate a random character
            char randomChar = CHARACTERS.charAt(random.nextInt(CHARACTERS.length()));

            // Determine a random insertion position
            int insertPosition = random.nextInt(input.length() + 1);

            // Insert the character at the random position
            result.insert(insertPosition, randomChar);
        }
        return result.toString();
    }

    //AI Generated
    private static final String HTML_SIMPLE = "<html atr=\"hello\"> World </html>";

    private static final String HTML_STRUCTURE = "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "</head>\n" +
            "<body>\n" +
            "</body>\n" +
            "</html>\n";

    private static final String HEAD_SECTION =
            "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\">\n" +
                    "    <title>Simple HTML Website</title>\n";

    private static final String CSS_STYLES =
            "    <style>\n" +
                    "        body {\n" +
                    "            font-family: Arial, sans-serif;\n" +
                    "            background-color: #f4f4f4;\n" +
                    "            margin: 0;\n" +
                    "            padding: 0;\n" +
                    "        }\n" +
                    "        header, footer {\n" +
                    "            background-color: #333;\n" +
                    "            color: white;\n" +
                    "            text-align: center;\n" +
                    "            padding: 10px 0;\n" +
                    "        }\n" +
                    "        nav {\n" +
                    "            background-color: #444;\n" +
                    "            padding: 10px;\n" +
                    "            text-align: center;\n" +
                    "        }\n" +
                    "        nav a {\n" +
                    "            color: white;\n" +
                    "            margin: 0 15px;\n" +
                    "            text-decoration: none;\n" +
                    "        }\n" +
                    "        article {\n" +
                    "            margin: 20px;\n" +
                    "            padding: 20px;\n" +
                    "            background-color: white;\n" +
                    "            border-radius: 8px;\n" +
                    "            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);\n" +
                    "        }\n" +
                    "        footer {\n" +
                    "            font-size: 0.8em;\n" +
                    "        }\n" +
                    "        table {\n" +
                    "            width: 100%;\n" +
                    "            border-collapse: collapse;\n" +
                    "            margin-top: 20px;\n" +
                    "        }\n" +
                    "        table, th, td {\n" +
                    "            border: 1px solid #ddd;\n" +
                    "        }\n" +
                    "        th, td {\n" +
                    "            padding: 10px;\n" +
                    "            text-align: left;\n" +
                    "        }\n" +
                    "    </style>\n";


}
