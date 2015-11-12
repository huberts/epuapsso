package controllers

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.zip.{DeflaterOutputStream, Deflater}
import javax.xml.namespace.QName
import javax.xml.soap.{MessageFactory, SOAPMessage, SOAPConnectionFactory}

import com.google.inject.Inject
import org.apache.commons.codec.binary.Base64
import play.api.Configuration
import play.api.mvc._

class Application @Inject() (configuration: Configuration) extends Controller {

  def index = Action { request =>
    request.session.get("TGSID").map { tgsid =>
      Ok("Logged in via EPUAP with TGSID=" + tgsid)
    }.getOrElse {
      Redirect(routes.Application.sendAuthnRequest())
    }
  }

  def sendAuthnRequest = Action { request =>
    val baos = new ByteArrayOutputStream()
    val dos = new DeflaterOutputStream(baos, new Deflater(Deflater.DEFAULT_COMPRESSION, true))
    dos.write(views.xml.authnRequest.render().toString().getBytes("UTF-8"))
    dos.close()
    baos.close()
    Redirect("https://hetmantest.epuap.gov.pl/DracoEngine2/draco.jsf?SAMLRequest=" + URLEncoder.encode(new String(Base64.encodeBase64(baos.toByteArray)), "UTF-8"))
  }

  def resolveArtifact(SAMLart: Option[String]) = Action {
    val connection = SOAPConnectionFactory.newInstance().createConnection()
    val response = connection.call(createRequest(SAMLart.getOrElse("missing")), "https://hetmantest.epuap.gov.pl/axis2/services/EngineSAMLArtifact")

    val baos = new ByteArrayOutputStream()
    response.writeTo(baos)
    baos.close()
    Ok(new String(baos.toByteArray, "UTF-8")).as("xml")
  }

  private def createRequest(artifact: String) = {
    val  request = MessageFactory.newInstance().createMessage()

    val envelope = request.getSOAPPart.getEnvelope
    envelope.addNamespaceDeclaration("soap", "http://www.w3.org/2003/05/soap-envelope")
    envelope.addNamespaceDeclaration("urn", "urn:oasis:names:tc:SAML:2.0:protocol")
    envelope.addNamespaceDeclaration("urn1", "urn:oasis:names:tc:SAML:2.0:assertion")

    val body = envelope.getBody
    val artifactResolveElem = body.addChildElement("ArtifactResolve", "urn")
    artifactResolveElem.addAttribute(new QName("ID"), java.util.UUID.randomUUID().toString)
    artifactResolveElem.addAttribute(new QName("Version"), "2.0")
    artifactResolveElem.addAttribute(new QName("Destination"), "https://hetmantest.epuap.gov.pl/DracoEngine2/draco.jsf")
    artifactResolveElem.addAttribute(new QName("IssueInstant"), java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString)
    artifactResolveElem.addAttribute(new QName("Consent"), "")

    val issuerElem = artifactResolveElem.addChildElement("Issuer", "urn1")
    issuerElem.addAttribute(new QName("Format"), "")
    issuerElem.addAttribute(new QName("SPProvidedID"), "")
    issuerElem.addTextNode(configuration.getString("epuapsso.issuer").getOrElse("issuer"))

    val artifactElem = artifactResolveElem.addChildElement("Artifact", "urn")
    artifactElem.addTextNode(artifact)

    request.getMimeHeaders.addHeader("SOAPAction", "https://hetmantest.epuap.gov.pl/axis2/services/EngineSAMLArtifact/artifactResolve")
    request.saveChanges()
    request
  }

}