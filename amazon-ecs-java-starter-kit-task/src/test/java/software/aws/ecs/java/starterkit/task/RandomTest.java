package software.aws.ecs.java.starterkit.task;

import java.util.Random;

import org.junit.jupiter.api.Test;

public class RandomTest {

	@Test
	void test() {
		for (int i = 0; i < 10; i++) {
			int waitTime = (1 + new Random().nextInt(3)) * 60000;
			System.out.println("Wait_time - " + i + ": " + waitTime);
		}
	}

}
