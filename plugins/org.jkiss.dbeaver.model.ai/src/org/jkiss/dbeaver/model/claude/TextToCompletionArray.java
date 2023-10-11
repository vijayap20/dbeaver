package org.jkiss.dbeaver.model.claude;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextToCompletionArray {
	
    public void processCompletionText(String text){
//        String text = "data: {\"completion\":\". ### SQL query to list customer details whose name starts with M: ```sql SELECT * FROM customer WHERE customer_name LIKE 'M%'; ``` This will select all rows from the customer table where the customer_name column starts with 'M'. The LIKE operator is used to match a pattern rather than exact values.\\n\\nHere is a SQL query to list customer details whose name starts with M from the given schema:\\n\\n```sql\\nSELECT \\n  c.customer_name,\",\"stop_reason\":null,\"model\":\"claude-2.0\",\"truncated\":false,\"stop\":null,\"log_id\":\"ae34e83a533fea1169964bbe10d023787822d4911dab69fccc936188cb334840\",\"exception\":null}\n" +
//                "data: {\"completion\":\". ### SQL query to list customer details whose name starts with M: ```sql SELECT * FROM customer WHERE customer_name LIKE 'M%'; ``` This will select all rows from the customer table where the customer_name column starts with 'M'. The LIKE operator is used to match a pattern rather than exact values.\\n\\nHere is a SQL query to list customer details whose name starts with M from the given schema:\\n\\n```sql\\nSELECT \\n  c.customer_name,\\n \",\"stop_reason\":null,\"model\":\"claude-2.0\",\"truncated\":false,\"stop\":null,\"log_id\":\"ae34e83a533fea1169964bbe10d023787822d4911dab69fccc936188cb334840\",\"exception\":null}\n" +
//                // ... (include the rest of your text blocks here)
//                "data: {\"completion\":\". ### SQL query to list customer details whose name starts with M: ```sql SELECT * FROM customer WHERE customer_name LIKE 'M%'; ``` This will select all rows from the customer table where the customer_name column starts with 'M'. The LIKE operator is used to match a pattern rather than exact values.\\n\\nHere is a SQL query to list customer details whose name starts with M from the given schema:\\n\\n```sql\\nSELECT \\n  c.customer_name,\\n  c\",\"stop_reason\":\"max_tokens\",\"model\":\"claude-2.0\",\"truncated\":false,\"stop\":null,\"log_id\":\"ae34e83a533fea1169964bbe10d023787822d4911dab69fccc936188cb334840\",\"exception\":null}";

        ArrayList<String> completions = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"completion\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            completions.add(matcher.group(1));
        }

        String[] completionArray = completions.toArray(new String[0]);

        // Print the extracted completions
        for (String completion : completionArray) {
            System.out.println(completion);
        }
    }
}