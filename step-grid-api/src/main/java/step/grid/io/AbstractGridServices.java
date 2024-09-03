package step.grid.io;

import java.io.PrintWriter;
import java.io.StringWriter;

public abstract class AbstractGridServices {

    protected OutputMessage handleUnexpectedError(Exception e) {
        OutputMessage output = newAgentErrorOutput(new AgentError(AgentErrorCode.UNEXPECTED));
        output.addAttachment(generateAttachmentForException(e));
        return output;
    }

    protected OutputMessage handleUnexpectedError(InputMessage inputMessage, Exception e) {
        OutputMessage output = newAgentErrorOutput(new AgentError(AgentErrorCode.UNEXPECTED));
        output.addAttachment(generateAttachmentForException(e));
        return output;
    }

    protected OutputMessage newAgentErrorOutput(AgentError error, Attachment...attachments) {
        OutputMessage output = new OutputMessage();
        output.setAgentError(error);
        if(attachments!=null) {
            for (Attachment attachment : attachments) {
                output.addAttachment(attachment);
            }
        }
        return output;
    }

    protected Attachment generateAttachmentForException(Throwable e) {
        Attachment attachment = new Attachment();
        attachment.setName("exception.log");
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        attachment.setHexContent(AttachmentHelper.getHex(w.toString().getBytes()));
        return attachment;
    }

    protected Attachment generateAttachmentForStacktrace(String attachmentName, StackTraceElement[] e) {
        Attachment attachment = new Attachment();
        StringWriter str = new StringWriter();
        PrintWriter w = new PrintWriter(str);
        for (StackTraceElement traceElement : e)
            w.println("\tat " + traceElement);
        attachment.setName(attachmentName);
        attachment.setHexContent(AttachmentHelper.getHex(str.toString().getBytes()));
        return attachment;
    }
}
