package software.aws.ecs.java.starterkit.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ECSTaskMetadataParserTest {

	@Test
	void test() {
		String filePath = "./src/main/resources/ecs_task_metadata_response_sample.json";
		String jsonString = "";
		try {
			jsonString = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
			System.out.println(jsonString);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		JsonParser parser = new JsonParser();  
	    JsonElement rootNode = parser.parse(jsonString);
	    if (rootNode.isJsonObject()) { 
	         JsonObject details = rootNode.getAsJsonObject();  
	         JsonElement taskARN = details.get("TaskARN"); 
	         System.out.println("TaskARN: " +taskARN.getAsString());
	         String taskARNString = taskARN.getAsString();
	         System.out.println("String: " + taskARNString);
	    }
	}

	
	 
     
}
