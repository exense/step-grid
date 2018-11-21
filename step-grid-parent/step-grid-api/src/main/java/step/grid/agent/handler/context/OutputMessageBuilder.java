/*******************************************************************************
 * (C) Copyright 2018 Jerome Comte and Dorian Cransac
 *
 * This file is part of STEP
 *
 * STEP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * STEP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with STEP.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package step.grid.agent.handler.context;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import step.grid.io.Attachment;
import step.grid.io.AttachmentHelper;
import step.grid.io.OutputMessage;

/**
 * A builder for OutputMessage instances.
 * 
 * @author Jérôme Comte
 * @author Dorian Cransac
 *
 */
public class OutputMessageBuilder {
	
	private ObjectNode payload;
	
	private String payloadJson;
	
	private List<Attachment> attachments;
	
	public OutputMessageBuilder() {
		super();
		
		payload = new ObjectMapper().createObjectNode();
	}
	
	public ObjectNode getPayload() {
		return payload;
	}

	public void setPayload(ObjectNode payloadBuilder) {
		this.payload = payloadBuilder;
	}

	/**
	 * Adds an output attribute  
	 * If the object contains a mapping for the specified name, this method replaces the old value with the specified value.
	 * 
	 * @param name the name of the output attribute
	 * @param value the value of the output attribute
	 * @return this instance
	 */
	public OutputMessageBuilder add(String name, boolean value) {
		payload.put(name, value);
		return this;
	}

	/**
	 * Adds an output attribute  
	 * If the object contains a mapping for the specified name, this method replaces the old value with the specified value.
	 * 
	 * @param name the name of the output attribute
	 * @param value the value of the output attribute
	 * @return this instance
	 */
	public OutputMessageBuilder add(String name, double value) {
		payload.put(name, value);
		return this;
	}

	/**
	 * Adds an output attribute  
	 * If the object contains a mapping for the specified name, this method replaces the old value with the specified value.
	 * 
	 * @param name the name of the output attribute
	 * @param value the value of the output attribute
	 * @return this instance
	 */
	public OutputMessageBuilder add(String name, int value) {
		payload.put(name, value);
		return this;
	}

	/**
	 * Adds an output attribute  
	 * If the object contains a mapping for the specified name, this method replaces the old value with the specified value.
	 * 
	 * @param name the name of the output attribute
	 * @param value the value of the output attribute
	 * @return this instance
	 */
	public OutputMessageBuilder add(String name, long value) {
		payload.put(name, value);
		return this;
	}
	
	/**
	 * Adds an output attribute  
	 * If the object contains a mapping for the specified name, this method replaces the old value with the specified value.
	 * 
	 * @param name the name of the output attribute
	 * @param value the value of the output attribute
	 * @return this instance
	 */
	public OutputMessageBuilder add(String name, String value) {
		payload.put(name, value);
		return this;
	}
	
	/**
	 * @return the payload of this output. This has no eff
	 * 
	 */
	public String getPayloadJson() {
		return payloadJson;
	}

	/**
	 * 
	 * @param payloadJson the payload of this output.
	 */
	public void setPayloadJson(String payloadJson) {
		this.payloadJson = payloadJson;
	}

	/**
	 * Adds attachments to the output
	 * 
	 * @param attachments the list of attachments to be added to the output
	 */
	public void addAttachments(List<Attachment> attachments) {
		createAttachmentListIfNeeded();
		attachments.addAll(attachments);
	}

	/**
	 * Adds an attachment to the output
	 * 
	 * @param attachment the attachment to be added to the output
	 */
	public void addAttachment(Attachment attachment) {
		createAttachmentListIfNeeded();
		attachments.add(attachment);
	}

	private void createAttachmentListIfNeeded() {
		if(attachments==null) {
			attachments = new ArrayList<>();
		}
	}
	
	/**
	 * Builds the output instance
	 * 
	 * @return the output message
	 */
	public OutputMessage build() {
		OutputMessage message = new OutputMessage();
		JsonNode result;
		if(payloadJson==null) {
			result = payload;			
		} else {
			ObjectMapper mapper = new ObjectMapper();

			try {
				result = mapper.reader().readTree(payloadJson);
			} catch (IOException e) {
				throw new RuntimeException("Error while parsing json "+payloadJson, e);
			};				
		}
		message.setPayload(result);
		message.setAttachments(attachments);
		return message;
	}

	private Attachment generateAttachmentForException(Throwable e) {
		Attachment attachment = new Attachment();	
		attachment.setName("exception.log");
		StringWriter w = new StringWriter();
		e.printStackTrace(new PrintWriter(w));
		attachment.setHexContent(AttachmentHelper.getHex(w.toString().getBytes()));
		return attachment;
	}
}
