package io.hsiao.gitmerge.mail;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public final class Mail {
  public static final String RECIPIENT_TYPE_BCC = "BCC";
  public static final String RECIPIENT_TYPE_CC = "CC";
  public static final String RECIPIENT_TYPE_TO = "TO";

  private final Session session;
  private final MimeMessage message;
  private final MimeBodyPart messageBodyPart;
  private final List<MimeBodyPart> attachsBodyPart;

  public Mail(final Properties props) {
    if (props == null) {
      throw new NullPointerException("argument 'props' is null");
    }

    session = Session.getInstance(props, null);
    message = new MimeMessage(session);
    messageBodyPart = new MimeBodyPart();
    attachsBodyPart = new LinkedList<>();
  }

  public static Properties getProperties(final String smtpHost, final String smtpPort) {
    if (smtpHost == null) {
      throw new NullPointerException("argument 'smtpHost' is null");
    }

    if (smtpPort == null) {
      throw new NullPointerException("argument 'smtpPort' is null");
    }

    final Properties props = new Properties();

    props.put("mail.smtp.host", smtpHost);
    props.put("mail.smtp.port", smtpPort);

    return props;
  }

  public void setContent(final Object object, final String type) throws Exception {
    if (object == null) {
      throw new NullPointerException("argument 'object' is null");
    }

    if (type == null) {
      throw new NullPointerException("argument 'type' is null");
    }

    messageBodyPart.setContent(object, type);
  }

  public void setFrom(final String address) throws Exception {
    message.setFrom(address);
  }

  public void setRecipients(final String type, final List<String> addresses, final String domain) throws Exception {
    if (type == null) {
      throw new NullPointerException("argument 'type' is null");
    }

    final Message.RecipientType recipientType = (Message.RecipientType) Message.RecipientType.class.getField(type).get(null);

    final String recipientAddresses;
    if (addresses == null) {
      recipientAddresses = null;
    }
    else {
      final StringBuilder sb = new StringBuilder();

      for (String address: addresses) {
        address = address.trim();
        if (address.isEmpty()) {
          continue;
        }

        if (!address.contains("@")) {
          if (domain == null) {
            throw new RuntimeException("argument 'domain' is null");
          }
          sb.append(address + "@" + domain);
        }
        else {
          sb.append(address);
        }

        sb.append(",");
      }

      recipientAddresses = sb.toString();
    }

    message.setRecipients(recipientType, recipientAddresses);
  }

  public void setSentDate(final Date date) throws Exception {
    message.setSentDate(date);
  }

  public void setSubject(final String subject, final String charset) throws Exception {
    message.setSubject(subject, charset);
  }

  public void attachFile(final String file) throws Exception {
    if (file == null) {
      throw new NullPointerException("argument 'file' is null");
    }

    final MimeBodyPart attachBodyPart = new MimeBodyPart();
    attachBodyPart.attachFile(file);
    attachsBodyPart.add(attachBodyPart);
  }

  public void send(final String username, final String password) throws Exception {
    final Multipart multipart = new MimeMultipart();
    multipart.addBodyPart(messageBodyPart);

    for (final MimeBodyPart attachBodyPart: attachsBodyPart) {
      multipart.addBodyPart(attachBodyPart);
    }

    message.setContent(multipart);

    Transport.send(message, username, password);
  }

  public static String getMailAddress(final String local, final String domain) {
    return String.format("%s@%s", local, domain);
  }
}
