package net.jimblackler.jsonschemafriend;

import java.net.URI;

public abstract class ValidationError {
  private final URI uri;
  private final Object document;
  private final Schema schema;
  private final Object object;

  protected ValidationError(URI uri, Object document, Schema schema) {
    this.uri = uri;
    this.document = document;
    this.schema = schema;
    Object _object;
    try {
      _object = Validator.getObject(document, uri);
    } catch (MissingPathException e) {
      _object = null;
    }
    object = _object;
  }

  @Override
  public String toString() {
    URI schemaPath = schema.getUri();

    String string = object == null ? "" : object.toString();
    return (string.length() <= 20 ? "\"" + string + "\" " : "")
        + (uri.toString().isEmpty() ? "" : "at " + uri + " ") + "failed "
        + (schemaPath.toString().isEmpty() ? "" : "against " + schemaPath + " ") + "with \""
        + getMessage() + "\"";
  }

  public Object getObject() {
    return object;
  }

  public URI getUri() {
    return uri;
  }

  public Object getDocument() {
    return document;
  }

  public Schema getSchema() {
    return schema;
  }

  public abstract String getMessage();
}
