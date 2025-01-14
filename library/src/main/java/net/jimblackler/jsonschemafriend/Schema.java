package net.jimblackler.jsonschemafriend;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;
import static net.jimblackler.jsonschemafriend.MetaSchemaDetector.detectMetaSchema;
import static net.jimblackler.jsonschemafriend.PathUtils.append;
import static net.jimblackler.jsonschemafriend.PathUtils.fixUnescaped;
import static net.jimblackler.jsonschemafriend.PathUtils.resolve;
import static net.jimblackler.jsonschemafriend.Utils.getOrDefault;
import static net.jimblackler.jsonschemafriend.Utils.setOf;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A schema defined by an object. "Object" refers to the type in the definition, not the type of
 * data it validates.
 */
public class Schema {
  private static final Logger LOG = Logger.getLogger(Schema.class.getName());

  private final Object schemaObject; // Kept for debugging only.

  private final SchemaStore schemaStore;
  private final URI uri;
  // number checks
  private final Number multipleOf;
  private final Number maximum;
  private final Object exclusiveMaximum;
  private final Number minimum;
  private final Object exclusiveMinimum;
  private final Number divisibleBy;
  // string checks
  private final Number maxLength;
  private final Number minLength;
  private final String pattern;
  private final String format;
  private final String contentEncoding;
  private final String contentMediaType;
  // array checks
  private final List<Schema> prefixItems;
  private final Schema additionalItems;
  private final Schema unevaluatedItems;
  private final Schema _items;
  private final List<Schema> itemsTuple;
  private final Number maxItems;
  private final Number minItems;
  private final boolean uniqueItems;
  private final Schema contains;
  private final Number minContains;
  private final Number maxContains;
  // object checks
  private final Number maxProperties;
  private final Number minProperties;
  private final Collection<String> requiredProperties = new HashSet<>();
  private final boolean required;
  private final Schema additionalProperties;
  private final Schema unevaluatedProperties;
  private final Map<String, Schema> _properties = new LinkedHashMap<>();
  private final Collection<String> patternPropertiesPatterns = new ArrayList<>();
  private final Collection<Schema> patternPropertiesSchemas = new ArrayList<>();
  private final Map<String, Collection<String>> dependentRequired = new HashMap<>();
  private final Map<String, Schema> dependentSchemas = new HashMap<>();
  private final Schema propertyNames;
  // all types checks
  private final boolean hasConst;
  private final Object _const;
  private final List<Object> enums;
  private final Set<String> explicitTypes;
  private final Collection<Schema> typesSchema = new HashSet<>();
  private final Collection<String> disallow = new HashSet<>();
  private final Collection<Schema> disallowSchemas = new HashSet<>();
  private final Object defaultValue;
  // in-place applicators
  private final Schema _if;
  private final Schema _then;
  private final Schema _else;
  private final Collection<Schema> allOf = new ArrayList<>();
  private final Collection<Schema> anyOf;
  private final Collection<Schema> oneOf;
  private final Schema not;
  private final Schema ref;
  private final Schema recursiveRef;
  private final boolean recursiveAnchor;

  private final List<Object> examples;
  private final String title;
  private final String description;

  private URI metaSchema;

  // Own
  private Schema parent;

  Schema(SchemaStore schemaStore, URI uri) throws GenerationException {
    this.schemaStore = schemaStore;
    this.uri = uri;

    schemaStore.register(uri, this);

    Object _schemaObject = schemaStore.getObject(uri);
    if (_schemaObject == null) {
      LOG.warning("No match for " + uri);
      // By design, if we can't find a schema definition, we log a warning but generate a default
      // schema that permits everything.
      _schemaObject = true;
    }

    schemaObject = _schemaObject;
    Map<String, Object> jsonObject;
    if (schemaObject instanceof Map) {
      jsonObject = (Map<String, Object>) schemaObject;
    } else {
      jsonObject = new LinkedHashMap<>();
    }

    // number checks
    multipleOf = (Number) jsonObject.get("multipleOf");
    maximum = (Number) jsonObject.get("maximum");
    exclusiveMaximum = jsonObject.get("exclusiveMaximum");
    minimum = (Number) jsonObject.get("minimum");
    exclusiveMinimum = jsonObject.get("exclusiveMinimum");
    divisibleBy = (Number) jsonObject.get("divisibleBy");

    // string checks
    maxLength = (Number) jsonObject.get("maxLength");
    minLength = (Number) jsonObject.get("minLength");
    Object patternObject = jsonObject.get("pattern");

    String _pattern = null;
    if (patternObject != null) {
      _pattern = (String) patternObject;
    }
    pattern = _pattern;

    Object formatObject = jsonObject.get("format");
    format = formatObject instanceof String ? (String) formatObject : null;

    Object contentEncodingObject = jsonObject.get("contentEncoding");
    contentEncoding =
        contentEncodingObject instanceof String ? (String) contentEncodingObject : null;

    Object contentMediaTypeObject = jsonObject.get("contentMediaType");
    contentMediaType =
        contentMediaTypeObject instanceof String ? (String) contentMediaTypeObject : null;

    // array checks
    Object prefixItemsObject = jsonObject.get("prefixItems");
    URI prefixItemsPath = append(uri, "prefixItems");
    if (prefixItemsObject instanceof List) {
      prefixItems = new ArrayList<>();
      Collection<Object> jsonArray = (Collection<Object>) prefixItemsObject;
      for (int idx = 0; idx != jsonArray.size(); idx++) {
        prefixItems.add(getSubSchema(append(prefixItemsPath, String.valueOf(idx))));
      }
    } else {
      prefixItems = null;
    }

    additionalItems = getSubSchema(jsonObject, "additionalItems", uri);
    unevaluatedItems = getSubSchema(jsonObject, "unevaluatedItems", uri);

    Object itemsObject = jsonObject.get("items");
    URI itemsPath = append(uri, "items");
    if (itemsObject instanceof List) {
      itemsTuple = new ArrayList<>();
      Collection<Object> jsonArray = (Collection<Object>) itemsObject;
      for (int idx = 0; idx != jsonArray.size(); idx++) {
        itemsTuple.add(getSubSchema(append(itemsPath, String.valueOf(idx))));
      }
      _items = null;
    } else {
      itemsTuple = null;
      _items = getSubSchema(jsonObject, "items", uri);
    }

    maxItems = (Number) jsonObject.get("maxItems");
    minItems = (Number) jsonObject.get("minItems");
    uniqueItems = getOrDefault(jsonObject, "uniqueItems", false);
    contains = getSubSchema(jsonObject, "contains", uri);
    minContains = (Number) jsonObject.get("minContains");
    maxContains = (Number) jsonObject.get("maxContains");

    // object checks
    maxProperties = (Number) jsonObject.get("maxProperties");
    minProperties = (Number) jsonObject.get("minProperties");

    Object requiredObject = jsonObject.get("required");
    if (requiredObject instanceof List) {
      for (Object req : (Iterable<Object>) requiredObject) {
        requiredProperties.add((String) req);
      }
    }
    required = requiredObject instanceof Boolean && (Boolean) requiredObject;

    additionalProperties = getSubSchema(jsonObject, "additionalProperties", uri);
    unevaluatedProperties = getSubSchema(jsonObject, "unevaluatedProperties", uri);

    Object propertiesObject = jsonObject.get("properties");
    if (propertiesObject instanceof Map) {
      Map<String, Object> properties = (Map<String, Object>) propertiesObject;
      URI propertiesPointer = append(uri, "properties");
      for (String propertyName : properties.keySet()) {
        URI propertyUri = append(propertiesPointer, propertyName);
        _properties.put(propertyName, getSubSchema(propertyUri));
      }
    }

    Object patternPropertiesObject = jsonObject.get("patternProperties");
    if (patternPropertiesObject instanceof Map) {
      Map<String, Object> patternProperties = (Map<String, Object>) patternPropertiesObject;
      URI propertiesPointer = append(uri, "patternProperties");
      for (String propertyPattern : patternProperties.keySet()) {
        patternPropertiesPatterns.add(propertyPattern);
        URI patternPointer = append(propertiesPointer, propertyPattern);
        patternPropertiesSchemas.add(getSubSchema(patternPointer));
      }
    }

    Map<String, Object> dependenciesJsonObject =
        (Map<String, Object>) jsonObject.get("dependencies");
    if (dependenciesJsonObject != null) {
      for (Map.Entry<String, Object> entry : dependenciesJsonObject.entrySet()) {
        String dependency = entry.getKey();
        Collection<String> spec = new ArrayList<>();
        Object dependencyObject = entry.getValue();
        if (dependencyObject instanceof List) {
          for (Object o : (Iterable<Object>) dependencyObject) {
            spec.add((String) o);
          }
          dependentRequired.put(dependency, spec);
        } else if (dependencyObject instanceof Map || dependencyObject instanceof Boolean) {
          URI dependenciesPointer = append(uri, "dependencies");
          dependentSchemas.put(dependency, getSubSchema(append(dependenciesPointer, dependency)));
        } else {
          Collection<String> objects = new ArrayList<>();
          objects.add((String) dependencyObject);
          dependentRequired.put(dependency, objects);
        }
      }
    }

    Map<String, Object> dependentRequiredJsonObject =
        (Map<String, Object>) jsonObject.get("dependentRequired");
    if (dependentRequiredJsonObject != null) {
      for (Map.Entry<String, Object> entry : dependentRequiredJsonObject.entrySet()) {
        Collection<String> spec = new ArrayList<>();
        for (Object req : (Iterable<Object>) entry.getValue()) {
          spec.add((String) req);
        }
        dependentRequired.put(entry.getKey(), spec);
      }
    }

    Map<String, Object> dependentSchemasJsonObject =
        (Map<String, Object>) jsonObject.get("dependentSchemas");
    if (dependentSchemasJsonObject != null) {
      for (String dependency : dependentSchemasJsonObject.keySet()) {
        URI dependenciesPointer = append(uri, "dependentSchemas");
        dependentSchemas.put(dependency, getSubSchema(append(dependenciesPointer, dependency)));
      }
    }

    propertyNames = getSubSchema(jsonObject, "propertyNames", uri);

    // all types checks
    if (jsonObject.containsKey("const")) {
      hasConst = true;
      _const = jsonObject.get("const");
    } else {
      hasConst = false;
      _const = null;
    }

    Collection<Object> enumArray = (Collection<Object>) jsonObject.get("enum");
    if (enumArray == null) {
      enums = null;
    } else {
      enums = new ArrayList<>();
      enums.addAll(enumArray);
    }

    Object typeObject = jsonObject.get("type");
    if (typeObject instanceof List) {
      URI typePointer = append(uri, "type");
      explicitTypes = new HashSet<>();
      List<Object> array = (List<Object>) typeObject;
      for (int idx = 0; idx != array.size(); idx++) {
        Object arrayEntryObject = array.get(idx);
        if (arrayEntryObject instanceof Boolean || arrayEntryObject instanceof Map) {
          typesSchema.add(getSubSchema(append(typePointer, String.valueOf(idx))));
        } else {
          explicitTypes.add((String) arrayEntryObject);
        }
      }
    } else if (typeObject instanceof String) {
      explicitTypes = setOf(typeObject.toString());
    } else {
      explicitTypes = null;
    }

    _if = getSubSchema(jsonObject, "if", uri);
    _then = getSubSchema(jsonObject, "then", uri);
    _else = getSubSchema(jsonObject, "else", uri);

    Object allOfObject = jsonObject.get("allOf");
    if (allOfObject instanceof List) {
      Collection<Object> array = (Collection<Object>) allOfObject;
      URI arrayPath = append(uri, "allOf");
      for (int idx = 0; idx != array.size(); idx++) {
        URI indexPointer = append(arrayPath, String.valueOf(idx));
        allOf.add(getSubSchema(indexPointer));
      }
    }

    Object extendsObject = jsonObject.get("extends");
    if (extendsObject instanceof List) {
      URI arrayPath = append(uri, "extends");
      Collection<Object> array = (Collection<Object>) extendsObject;
      for (int idx = 0; idx != array.size(); idx++) {
        URI indexPointer = append(arrayPath, String.valueOf(idx));
        allOf.add(getSubSchema(indexPointer));
      }
    } else if (extendsObject instanceof Map || extendsObject instanceof Boolean) {
      URI arrayPath = append(uri, "extends");
      allOf.add(getSubSchema(arrayPath));
    }

    Object refObject = jsonObject.get("$ref");
    if (refObject instanceof String) {
      // Refs should be URL Escaped already; but in practice they are sometimes not.
      URI resolved = resolve(uri, URI.create(fixUnescaped((String) refObject)));
      ref = schemaStore.loadSchema(resolved, false);
    } else {
      ref = null;
    }

    Object recursiveRefObject = jsonObject.get("$recursiveRef");
    if (recursiveRefObject instanceof String) {
      URI resolved = resolve(uri, URI.create((String) recursiveRefObject));
      recursiveRef = schemaStore.loadSchema(resolved, false);
    } else {
      recursiveRef = null;
    }

    recursiveAnchor = getOrDefault(jsonObject, "$recursiveAnchor", false);

    Object anyOfObject = jsonObject.get("anyOf");
    if (anyOfObject instanceof List) {
      anyOf = new ArrayList<>();
      Collection<Object> array = (Collection<Object>) anyOfObject;
      URI arrayPath = append(uri, "anyOf");
      for (int idx = 0; idx != array.size(); idx++) {
        URI indexPointer = append(arrayPath, String.valueOf(idx));
        anyOf.add(getSubSchema(indexPointer));
      }
    } else {
      anyOf = null;
    }

    Object oneOfObject = jsonObject.get("oneOf");
    if (oneOfObject instanceof List) {
      oneOf = new ArrayList<>();
      Collection<Object> array = (Collection<Object>) oneOfObject;
      URI arrayPath = append(uri, "oneOf");
      for (int idx = 0; idx != array.size(); idx++) {
        URI indexPointer = append(arrayPath, String.valueOf(idx));
        oneOf.add(getSubSchema(indexPointer));
      }
    } else {
      oneOf = null;
    }

    not = getSubSchema(jsonObject, "not", uri);

    Object disallowObject = jsonObject.get("disallow");
    if (disallowObject instanceof String) {
      disallow.add(disallowObject.toString());
    } else if (disallowObject instanceof List) {
      List<Object> array = (List<Object>) disallowObject;
      URI disallowPointer = append(uri, "disallow");
      for (int idx = 0; idx != array.size(); idx++) {
        Object disallowEntryObject = array.get(idx);
        if (disallowEntryObject instanceof String) {
          disallow.add((String) array.get(idx));
        } else {
          disallowSchemas.add(getSubSchema(append(disallowPointer, String.valueOf(idx))));
        }
      }
    }

    defaultValue = jsonObject.get("default");
    title = (String) jsonObject.get("title");
    description = (String) jsonObject.get("description");
    examples = (List<Object>) jsonObject.get("examples");

    if (examples != null) {
      Validator validator = new Validator();
      for (int idx = 0; idx != examples.size(); idx++) {
        try {
          validator.validate(this, jsonObject, URI.create("#/examples/" + idx));
        } catch (ValidationException e) {
          throw new GenerationException(e);
        }
      }
    }
  }

  private Schema getSubSchema(Map<String, Object> jsonObject, String name, URI uri)
      throws GenerationException {
    Object childObject = jsonObject.get(name);
    if (childObject instanceof Map || childObject instanceof Boolean) {
      return getSubSchema(append(uri, name));
    }
    return null;
  }

  private Schema getSubSchema(URI uri) throws GenerationException {
    Schema subSchema = schemaStore.loadSchema(uri, false);
    if (subSchema != null && subSchema.getUri().equals(uri)) {
      subSchema.setParent(this);
    }
    return subSchema;
  }

  public Boolean isFalse() {
    if (schemaObject instanceof Boolean) {
      return !(Boolean) schemaObject;
    }
    return false;
  }

  @Override
  public String toString() {
    return uri + " / " + schemaObject;
  }

  public boolean isExclusiveMinimumBoolean() {
    if (exclusiveMinimum instanceof Boolean) {
      return (Boolean) exclusiveMinimum;
    }
    return false;
  }

  public boolean isExclusiveMaximumBoolean() {
    if (exclusiveMaximum instanceof Boolean) {
      return (Boolean) exclusiveMaximum;
    }
    return false;
  }

  public Object getSchemaObject() {
    return schemaObject;
  }

  public URI getUri() {
    return uri;
  }

  public Number getMultipleOf() {
    return multipleOf;
  }

  public Number getMaximum() {
    return maximum;
  }

  public Number getExclusiveMaximum() {
    if (exclusiveMaximum instanceof Number) {
      return (Number) exclusiveMaximum;
    }
    return null;
  }

  public Number getMinimum() {
    return minimum;
  }

  public Number getExclusiveMinimum() {
    if (exclusiveMinimum instanceof Number) {
      return (Number) exclusiveMinimum;
    }
    return null;
  }

  public Number getDivisibleBy() {
    return divisibleBy;
  }

  public Number getMaxLength() {
    return maxLength;
  }

  public Number getMinLength() {
    return minLength;
  }

  public String getPattern() {
    return pattern;
  }

  public String getFormat() {
    return format;
  }

  public String getContentEncoding() {
    return contentEncoding;
  }

  public String getContentMediaType() {
    return contentMediaType;
  }

  public List<Schema> getPrefixItems() {
    return prefixItems == null ? null : Collections.unmodifiableList(prefixItems);
  }

  public Schema getAdditionalItems() {
    return additionalItems;
  }

  public Schema getUnevaluatedItems() {
    return unevaluatedItems;
  }

  public Schema getItems() {
    return _items;
  }

  public List<Schema> getItemsTuple() {
    return itemsTuple == null ? null : Collections.unmodifiableList(itemsTuple);
  }

  public Number getMaxItems() {
    return maxItems;
  }

  public Number getMinItems() {
    return minItems;
  }

  public Schema getContains() {
    return contains;
  }

  public boolean isUniqueItems() {
    return uniqueItems;
  }

  public Number getMinContains() {
    return minContains;
  }

  public Number getMaxContains() {
    return maxContains;
  }

  public Number getMaxProperties() {
    return maxProperties;
  }

  public Number getMinProperties() {
    return minProperties;
  }

  public Collection<String> getRequiredProperties() {
    return unmodifiableCollection(requiredProperties);
  }

  public boolean isRequired() {
    return required;
  }

  public Schema getAdditionalProperties() {
    return additionalProperties;
  }

  public Schema getUnevaluatedProperties() {
    return unevaluatedProperties;
  }

  public Map<String, Schema> getProperties() {
    return unmodifiableMap(_properties);
  }

  public Collection<String> getPatternPropertiesPatterns() {
    return unmodifiableCollection(patternPropertiesPatterns);
  }

  public Collection<Schema> getPatternPropertiesSchema() {
    return unmodifiableCollection(patternPropertiesSchemas);
  }

  public Map<String, Collection<String>> getDependentRequired() {
    return unmodifiableMap(dependentRequired);
  }

  public Map<String, Schema> getDependentSchemas() {
    return unmodifiableMap(dependentSchemas);
  }

  public Schema getPropertyNames() {
    return propertyNames;
  }

  public boolean hasConst() {
    return hasConst;
  }

  public Object getConst() {
    return _const;
  }

  public List<Object> getEnums() {
    return enums == null ? null : Collections.unmodifiableList(enums);
  }

  public Collection<String> getExplicitTypes() {
    if (explicitTypes == null) {
      return null;
    }
    return Collections.unmodifiableSet(explicitTypes);
  }

  public Collection<Schema> getTypesSchema() {
    return unmodifiableCollection(typesSchema);
  }

  public Schema getIf() {
    return _if;
  }

  public Schema getThen() {
    return _then;
  }

  public Schema getElse() {
    return _else;
  }

  public Collection<Schema> getAllOf() {
    return unmodifiableCollection(allOf);
  }

  public Collection<Schema> getAnyOf() {
    return anyOf == null ? null : unmodifiableCollection(anyOf);
  }

  public Collection<Schema> getOneOf() {
    return oneOf == null ? null : unmodifiableCollection(oneOf);
  }

  public Schema getNot() {
    return not;
  }

  public Schema getRef() {
    return ref;
  }

  public boolean isRecursiveAnchor() {
    return recursiveAnchor;
  }

  public Schema getRecursiveRef() {
    return recursiveRef;
  }

  public Collection<String> getDisallow() {
    return unmodifiableCollection(disallow);
  }

  public Collection<Schema> getDisallowSchemas() {
    return unmodifiableCollection(disallowSchemas);
  }

  public Object getDefault() {
    return defaultValue;
  }

  public List<Object> getExamples() {
    return examples;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Schema getParent() {
    return parent;
  }

  protected void setParent(Schema parent) {
    if (this.parent != null) {
      throw new IllegalStateException("Schemas may only have one parent");
    }
    this.parent = parent;
  }

  public URI getMetaSchema() {
    if (metaSchema == null) {
      metaSchema = detectMetaSchema(schemaStore.getBaseObject(uri));
    }
    return metaSchema;
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Schema)) {
      return false;
    }
    return uri.equals(((Schema) obj).getUri());
  }
}
