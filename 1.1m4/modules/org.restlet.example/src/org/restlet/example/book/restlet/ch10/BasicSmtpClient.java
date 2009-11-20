package org.restlet.example.book.restlet.ch10;

import org.restlet.Client;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

public class BasicSmtpClient {

    private static final String MAIL = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?>"
            + "<email>"
            + " <head>"
            + "  <subject>Test</subject>"
            + "  <from>restlet.testfr@yahoo.fr</from>"
            + "  <to>thboileau@gmail.com</to>"
            + "  <cc>thboileau@hotmail.com</cc>"
            + " </head>"
            + " <body><![CDATA[This is a simple message for you.]]></body>"
            + "</email>";

    public static void main(String[] args) {

        Representation mail = new StringRepresentation(MAIL, MediaType.TEXT_XML);

        // Sends the request and gets the response
        Request request = new Request(new Method("POST"),
                "smtp://smtp.mail.yahoo.com", mail);
        ChallengeResponse challengeResponse = new ChallengeResponse(
                ChallengeScheme.SMTP_PLAIN, "restlet.testfr", "saya08");
        request.setChallengeResponse(challengeResponse);

        // Instantiates a client according to a protocol
        Client client = new Client(Protocol.SMTP);
        // Sends the request
        Response response = client.handle(request);

        // Prints the status of the response
        System.out.println(response.getStatus());
    }
}