package com.recycleproj;

import java.io.IOException;

/** Sends a plain-text email. */
public interface Mailer {
	void send(String to, String subject, String body) throws IOException;

	/** For local testing without a mail server: prints each email to the console instead of sending it. */
	static Mailer console() {
		return (to, subject, body) -> System.out.println(
				"----- Email to " + to + " -----\nSubject: " + subject + "\n\n" + body + "\n-----------------------------");
	}
}
