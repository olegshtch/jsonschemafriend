package net.jimblackler.jsonschematypes.codegen;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JDefinedClass;

class JavaDefinedClassMaker {
  static JDefinedClass makeClassForSchema(JClassContainer classParent, String name, Client client) {
    /* Ensure no direct ancestor has the same name */
    while (true) {
      boolean changed = false;
      for (JClassContainer container = classParent; container instanceof JDefinedClass;
           container = container.parentContainer()) {
        JDefinedClass classContainer = (JDefinedClass) container;
        if (classContainer.name().equals(name)) {
          name = varyName(name);
          changed = true;
          break;
        }
      }
      if (!changed) {
        break;
      }
    }

    JDefinedClass _class;
    while (true) {
      try {
        _class = client.getClass(name);
        break;
      } catch (JClassAlreadyExistsException e) {
        name = varyName(name);
      }
    }
    return _class;
  }

  private static String varyName(String name) {
    for (int idx = 0; idx < name.length(); idx++) {
      try {
        int i = Integer.parseInt(name.substring(idx));
        return name.substring(0, idx) + (i + 1);
      } catch (NumberFormatException e) {
        // Ignored by design.
      }
    }
    return name + "2";
  }

  interface Client {
    JDefinedClass getClass(String name) throws JClassAlreadyExistsException;
  }
}
