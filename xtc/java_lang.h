/*
 * Object-Oriented Programming
 * Copyright (C) 2012 Robert Grimm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */

#pragma once

#include <stdint.h>
#include <string>
#include <iostream>

#include "ptr.h"

// ==========================================================================

// To avoid the "static initialization order fiasco", we use functions
// instead of fields/variables for all pointer values that are statically
// initialized.

// See http://www.parashift.com/c++-faq-lite/ctors.html#faq-10.14.

// ==========================================================================

namespace java {
  namespace lang {

    // Forward declarations of data layout and vtables.
    struct __Object;
    struct __Object_VT;

    struct __String;
    struct __String_VT;

    struct __Class;
    struct __Class_VT;

    // Definition of type names, which are equivalent to Java semantics,
    // i.e., a smart pointer to a data layout.
    typedef __rt::Ptr<__Object> Object;
    typedef __rt::Ptr<__Class> Class;
    typedef __rt::Ptr<__String> String;
  }
}

// ==========================================================================

namespace __rt {

  // The function returning the canonical null value.
  java::lang::Object null();

  // The template function for the virtual destructor.
  template <typename T>
  void __delete(T* addr) {
    delete addr;
  }

}

// ==========================================================================

namespace java {
  namespace lang {

    // The data layout for java.lang.Object.
    struct __Object {
      __Object_VT* __vptr;

      // The constructor.
      __Object();

      // The methods implemented by java.lang.Object.
      static int32_t m_hashCode(Object);
      static bool m_equals(Object, Object);
      static Class m_getClass(Object);
      static String m_toString(Object);
      static Object init(Object __this) { return __this; }  

      // The function returning the class object representing
      // java.lang.Object.
      static Class __class();

      // The vtable for java.lang.Object.
      static __Object_VT __vtable;
    };

    // The vtable layout for java.lang.Object.
    struct __Object_VT {
      Class __isa;
      void (*__delete)(__Object*);
      int32_t (*m_hashCode)(Object);
      bool (*m_equals)(Object, Object);
      Class (*m_getClass)(Object);
      String (*m_toString)(Object);

      __Object_VT()
      : __isa(__Object::__class()),
        __delete(&__rt::__delete<__Object>),
        m_hashCode(&__Object::m_hashCode),
        m_equals(&__Object::m_equals),
        m_getClass(&__Object::m_getClass),
        m_toString(&__Object::m_toString) {
      }
    };

    // ======================================================================

    // The data layout for java.lang.String.
    struct __String {
      __String_VT* __vptr;
      std::string data;

      // The constructor;
      __String(std::string data);

      // The methods implemented by java.lang.String.
      static int32_t m_hashCode(String);
      static bool m_equals(String, Object);
      static String m_toString(String);
      static int32_t length(String);
      static char charAt(String, int32_t);
      static String init(String __this) { return __this; }

      // The function returning the class object representing
      // java.lang.String.
      static Class __class();

      // The vtable for java.lang.String.
      static __String_VT __vtable;
    };

    std::ostream& operator<<(std::ostream& out, String);

    // The vtable layout for java.lang.String.
    struct __String_VT {
      Class __isa;
      void (*__delete)(__String*);
      int32_t (*m_hashCode)(String);
      bool (*m_equals)(String, Object);
      Class (*m_getClass)(String);
      String (*m_toString)(String);
      int32_t (*length)(String);
      char (*charAt)(String, int32_t);
      
      __String_VT()
      : __isa(__String::__class()),
        __delete(&__rt::__delete<__String>),
        m_hashCode(&__String::m_hashCode),
        m_equals(&__String::m_equals),
        m_getClass((Class(*)(String))&__Object::m_getClass),
        m_toString(&__String::m_toString),
        length(&__String::length),
        charAt(&__String::charAt) {
      }
    };

    // ======================================================================

    // The data layout for java.lang.Class.
    struct __Class {
      __Class_VT* __vptr;
      String name;
      Class parent;
      Class component;
      bool primitive;

      // The constructor.
      __Class(String name,
              Class parent,
              Class component = __rt::null(),
              bool primitive = false);

      // The instance methods of java.lang.Class.
      static String m_toString(Class);
      static String getName(Class);
      static Class getSuperclass(Class);
      static bool isPrimitive(Class);
      static bool isArray(Class);
      static Class getComponentType(Class);
      static bool isInstance(Class, Object);
      static Class init(Class __this) { return __this; }

      // The function returning the class object representing
      // java.lang.Class.
      static Class __class();

      // The vtable for java.lang.Class.
      static __Class_VT __vtable;
    };

    // The vtable layout for java.lang.Class.
    struct __Class_VT {
      Class __isa;
      void (*__delete)(__Class*);
      int32_t (*m_hashCode)(Class);
      bool (*m_equals)(Class, Object);
      Class (*m_getClass)(Class);
      String (*m_toString)(Class);
      String (*getName)(Class);
      Class (*getSuperclass)(Class);
      bool (*isPrimitive)(Class);
      bool (*isArray)(Class);
      Class (*getComponentType)(Class);
      bool (*isInstance)(Class, Object);

      __Class_VT()
      : __isa(__Class::__class()),
        __delete(&__rt::__delete<__Class>),
        m_hashCode((int32_t(*)(Class))&__Object::m_hashCode),
        m_equals((bool(*)(Class,Object))&__Object::m_equals),
        m_getClass((Class(*)(Class))&__Object::m_getClass),
        m_toString(&__Class::m_toString),
        getName(&__Class::getName),
        getSuperclass(&__Class::getSuperclass),
        isPrimitive(&__Class::isPrimitive),
        isArray(&__Class::isArray),
        getComponentType(&__Class::getComponentType),
        isInstance(&__Class::isInstance) {
      }
    };

    // ======================================================================

    // The completey incomplete data layout for java.lang.Integer.
    struct __Integer {

      // The class instance representing the primitive type int.
      static Class TYPE();

    };

    // ======================================================================

    // For simplicity, we use C++ inheritance for exceptions and throw
    // them by value.  In other words, the translator does not support
    // user-defined exceptions and simply relies on a few built-in
    // classes.
    class Throwable {
    };

    class Exception : public Throwable {
    };

    class RuntimeException : public Exception {
    };

    class NullPointerException : public RuntimeException {
    };

    class NegativeArraySizeException : public RuntimeException {
    };

    class ArrayStoreException : public RuntimeException {
    };

    class ClassCastException : public RuntimeException {
    };

    class IndexOutOfBoundsException : public RuntimeException {
    };

    class ArrayIndexOutOfBoundsException : public IndexOutOfBoundsException {
    };
    
  }
}

// ==========================================================================

namespace __rt {

  // ONE DIMENTIONAL ARRAYS
  template <typename T>
  struct Array;

  template <typename T>
  struct Array_VT;

  // The data layout for arrays.
  template <typename T>
  struct Array {
    Array_VT<T>* __vptr;
    const int32_t length;
    T* __data;

    // The constructor (defined inline).
    Array(const int32_t length)
    : __vptr(&__vtable), length(length), __data(new T[length]()) {
    }

    // The destructor.
    static void __delete(Array* addr) {
      delete[] addr->__data;
      delete addr;
    }

    // Array access.
    T& operator[](int32_t index) {
      if (0 > index || index >= length) {
        throw java::lang::ArrayIndexOutOfBoundsException();
      }
      return __data[index];
    }

    const T& operator[](int32_t index) const {
      if (0 > index || index >= length) {
        throw java::lang::ArrayIndexOutOfBoundsException();
      }
      return __data[index];
    }

    static Ptr<Array<T> > init(Ptr<Array<T> > __this, int32_t length) {
      if (length <= 0) {
        throw java::lang::NegativeArraySizeException();
      }
      return __this; 
    }

    // The function returning the class object representing the array.
    static java::lang::Class __class();

    // The vtable for the array.
    static Array_VT<T> __vtable;
  };

  template <typename T>
  struct Array_VT {
    typedef Ptr<Array<T> > Reference;

    java::lang::Class __isa;
    void (*__delete)(Array<T>*);
    int32_t (*m_hashCode)(Reference);
    bool (*m_equals)(Reference, java::lang::Object);
    java::lang::Class (*m_getClass)(Reference);
    java::lang::String (*m_toString)(Reference);
    
    Array_VT()
    : __isa(Array<T>::__class()),
      __delete(&Array<T>::__delete),
      m_hashCode((int32_t(*)(Reference))
               &java::lang::__Object::m_hashCode),
      m_equals((bool(*)(Reference,java::lang::Object))
             &java::lang::__Object::m_equals),
      m_getClass((java::lang::Class(*)(Reference))
               &java::lang::__Object::m_getClass),
      m_toString((java::lang::String(*)(Reference))
               &java::lang::__Object::m_toString) {
    }
  };

  // The vtable for arrays.  Note that this definition uses the default
  // no-arg constructor.
  template <typename T>
  Array_VT<T> Array<T>::__vtable;

  // TWO DIMENTIONAL ARRAYS

  // Forward declarations of data layout and vtable.
  template <typename T>
  struct Array2D;

  template <typename T>
  struct Array2D_VT;

  // The data layout for arrays.
  template <typename T>
  struct Array2D {
    Array2D_VT<T>* __vptr;
    const int32_t length;
    const int32_t length2;
    T** __data;

    // The constructor (defined inline).
    Array2D(const int32_t length, const int32_t length2)
    : __vptr(&__vtable), length(length), length2(length2), __data(new T*[length]()) {
      for(int32_t i=0; i<length; i++){
        __data[i] = new T[length2];
      }
    }

    // The destructor.
    static void __delete(Array2D* addr) {
      for(int i = 0; i < addr->length; ++i) {
        delete [] addr->__data[i];
      }
      delete[] addr->__data;
      delete addr;
    }

    struct Access {
      T* __subdata;
      int32_t length;
      
      Access(T* _array, int32_t len) : __subdata(_array), length(len) { }

      T& operator[](int32_t index) {
        if (0 > index || index >= length) {
          throw java::lang::ArrayIndexOutOfBoundsException();
        }
        return __subdata[index];
      }

      Access* operator->() {
        return this;
      }
    };

    // Array access.
    Access operator[](int32_t index) {
      if (0 > index || index >= length) {
        throw java::lang::ArrayIndexOutOfBoundsException();
      }
      return Access(__data[index], length2);
    }

    const Access operator[](int32_t index) const {
      if (0 > index || index >= length) {
        throw java::lang::ArrayIndexOutOfBoundsException();
      }
      return Access(__data[index], length2);
    }

    static Ptr<Array2D<T> > init(Ptr<Array2D<T> > __this, int32_t length, int32_t length2) {
      if (length <= 0 || length2 <= 0) {
        throw java::lang::NegativeArraySizeException();
      }
      return __this; 
    }

    // The function returning the class object representing the array.
    static java::lang::Class __class();

    // The vtable for the array.
    static Array2D_VT<T> __vtable;  
  };

  // The vtable for arrays.
  template <typename T>
  struct Array2D_VT {
    typedef Ptr<Array2D<T> > Reference;

    java::lang::Class __isa;
    void (*__delete)(Array2D<T>*);
    int32_t (*m_hashCode)(Reference);
    bool (*m_equals)(Reference, java::lang::Object);
    java::lang::Class (*m_getClass)(Reference);
    java::lang::String (*m_toString)(Reference);
    
    Array2D_VT()
    : __isa(Array2D<T>::__class()),
      __delete(&Array2D<T>::__delete),
      m_hashCode((int32_t(*)(Reference))
               &java::lang::__Object::m_hashCode),
      m_equals((bool(*)(Reference,java::lang::Object))
             &java::lang::__Object::m_equals),
      m_getClass((java::lang::Class(*)(Reference))
               &java::lang::__Object::m_getClass),
      m_toString((java::lang::String(*)(Reference))
               &java::lang::__Object::m_toString) {
    }
  };

  // The vtable for arrays.  Note that this definition uses the default
  // no-arg constructor.
  template <typename T>
  Array2D_VT<T> Array2D<T>::__vtable;

  // But where is the definition of __class()???

  // ========================================================================

  // Function for converting a C string lieral to a translated
  // Java string.
  inline java::lang::String literal(const char * s) {
    // C++ implicitly converts the C string to a std::string.
    return new java::lang::__String(s);
  }

  // ========================================================================

  // Template function to check against null values.
  template <typename T>
  void checkNotNull(T o) {
    if (null() == o) {
      throw java::lang::NullPointerException();
    }
  }

  // Template function to check array stores.
  template <typename T, typename U>
  void checkStore(Ptr<Array<T> > array, U object) {
    if (null() != object) {
      java::lang::Class t1 = array->__vptr->m_getClass(array);
      java::lang::Class t2 = t1->__vptr->getComponentType(t1);

      if (! t2->__vptr->isInstance(t2, object)) {
        throw java::lang::ArrayStoreException();
      }
    }
  }

    // Template function to check array stores.
  template <typename T, typename U>
  void checkStore2D(Ptr<Array2D<T> > array, U object) {
    if (null() != object) {
      java::lang::Class t1 = array->__vptr->m_getClass(array);
      java::lang::Class t2 = t1->__vptr->getComponentType(t1);

      if (! t2->__vptr->isInstance(t2, object)) {
        throw java::lang::ArrayStoreException();
      }
    }
  }

  // Template function for translated Java casts.
  template <typename T, typename U>
  T java_cast(U object) {
    java::lang::Class k = T::value_type::__class();
    
    if (! k->__vptr->isInstance(k, object)) {
      throw java::lang::ClassCastException();
    }

    return T(object);
  }

}

namespace __rt {
  typedef char byte;
}
