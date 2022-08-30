#include <jni.h>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <list>
#include <vector>
#include <pthread.h>
#include <unistd.h>
#include <utility>
#include "sha1.hpp"
#include "tools.h"
#include "log.h"

using namespace std;

class StringExist {
public:
    explicit StringExist(bool useMutex) {
        if (useMutex) {
            mutexCreated = pthread_mutex_init(&lock, nullptr) == 0;
        } else {
            mutexCreated = false;
        }
    }

    ~StringExist() {
        if (mutexCreated) {
            pthread_mutex_destroy(&lock);
            mutexCreated = false;
        }
    }

    void clear() {
        toggleMutex(true);
        content.clear();
        toggleMutex(false);
    }

    void insert(const string &name) {
        toggleMutex(true);
        content.push_back(name);
        toggleMutex(false);
    }

    void remove(const string &name) {
        toggleMutex(true);
        content.remove(name);
        toggleMutex(false);
    }

    bool has(const string &name, bool contains) {
        bool ret = false;
        toggleMutex(true);

        for (auto &i: content) {
            if (contains ? i.find(name) != string::npos : i == name) {
                ret = true;
                break;
            }
        }
        toggleMutex(false);
        return ret;
    }

    void toggleMutex(bool block) {
        if (mutexCreated) {
            block ? pthread_mutex_lock(&lock) : pthread_mutex_unlock(&lock);
        }
    }

private:
    list<string> content;
    bool mutexCreated;
    pthread_mutex_t lock{};
};

extern "C" JNIEXPORT jlong
Java_dev_ragnarok_fenrir_module_StringExist_init(JNIEnv *, jobject, jboolean useMutex) {
    return (jlong) (intptr_t) new StringExist(useMutex);
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_StringExist_destroy(JNIEnv *, jobject, jlong pointer) {
    if (!pointer) {
        return;
    }
    delete ((StringExist *) (intptr_t) pointer);
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_StringExist_clear(JNIEnv *, jobject, jlong pointer) {
    if (!pointer) {
        return;
    }
    auto *content = (StringExist *) (intptr_t) pointer;
    content->clear();
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_StringExist_insert(JNIEnv *env, jobject, jlong pointer,
                                                   jstring value) {
    if (!pointer) {
        return;
    }
    char const *textString = SafeGetStringUTFChars(env, value, nullptr);
    if (!textString) {
        return;
    }
    string v = textString;
    env->ReleaseStringUTFChars(value, textString);
    auto *content = (StringExist *) (intptr_t) pointer;
    content->insert(v);
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_StringExist_delete(JNIEnv *env, jobject, jlong pointer,
                                                   jstring value) {
    if (!pointer) {
        return;
    }
    char const *textString = SafeGetStringUTFChars(env, value, nullptr);
    if (!textString) {
        return;
    }
    string v = textString;
    env->ReleaseStringUTFChars(value, textString);
    auto *content = (StringExist *) (intptr_t) pointer;
    content->remove(v);
}

extern "C" JNIEXPORT jboolean
Java_dev_ragnarok_fenrir_module_StringExist_has(JNIEnv *env, jobject, jlong pointer, jstring value,
                                                jboolean contains) {
    if (!pointer) {
        return false;
    }
    char const *textString = SafeGetStringUTFChars(env, value, nullptr);
    if (!textString) {
        return false;
    }
    string v = textString;
    env->ReleaseStringUTFChars(value, textString);
    auto *content = (StringExist *) (intptr_t) pointer;
    return content->has(v, contains);
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_StringExist_lockMutex(JNIEnv *, jobject, jlong pointer,
                                                      jboolean lock) {
    if (!pointer) {
        return;
    }
    auto *content = (StringExist *) (intptr_t) pointer;
    content->toggleMutex(lock);
}

extern "C" JNIEXPORT jlong
Java_dev_ragnarok_fenrir_module_BufferWriteNative_allocateBuffer(JNIEnv *, jobject, jint size) {
    auto buf = new std::vector<char>;
    if (size > 0) {
        buf->reserve(size);
    }
    return (jlong) (intptr_t) buf;
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_BufferWriteNative_putBuffer(JNIEnv *env, jobject, jlong pointer,
                                                            jbyteArray array, jint size) {
    if (!pointer || size <= 0) {
        return;
    }
    jboolean isCopy;
    jint length = env->GetArrayLength(array);
    jbyte *buf = env->GetByteArrayElements(array, &isCopy);
    if (!buf) {
        return;
    }
    if (length < size) {
        LOGE("%s %d %d", "Size json error", length, size);
        return;
    }

    auto *bufPointer = (std::vector<char> *) (intptr_t) pointer;
    if (bufPointer->capacity() < bufPointer->size() + size) {
        bufPointer->reserve(bufPointer->size() + size + 8196);
    }
    bufPointer->insert(bufPointer->end(), buf, buf + size);

    env->ReleaseByteArrayElements(array, buf, JNI_ABORT);
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_BufferWriteNative_putBufferChar(JNIEnv *, jobject, jlong pointer,
                                                                jbyte value) {
    if (!pointer) {
        return;
    }

    auto *bufPointer = (std::vector<char> *) (intptr_t) pointer;
    if (bufPointer->capacity() < bufPointer->size() + 1) {
        bufPointer->reserve(bufPointer->size() + 1 + 8196);
    }
    bufPointer->push_back(value);
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_BufferWriteNative_endString(JNIEnv *, jobject, jlong pointer) {
    if (!pointer) {
        return;
    }

    auto *bufPointer = (std::vector<char> *) (intptr_t) pointer;
    if (bufPointer->capacity() < bufPointer->size() + 1) {
        bufPointer->reserve(bufPointer->size() + 1 + 8196);
    }
    (*bufPointer)[bufPointer->size()] = '\0';
}

extern "C" JNIEXPORT jint
Java_dev_ragnarok_fenrir_module_BufferWriteNative_bufferSize(JNIEnv *, jobject, jlong pointer) {
    if (!pointer) {
        return 0;
    }

    auto *bufPointer = (std::vector<char> *) (intptr_t) pointer;
    return (int) bufPointer->size();
}

extern "C" JNIEXPORT void
Java_dev_ragnarok_fenrir_module_BufferWriteNative_releaseBuffer(JNIEnv *, jobject, jlong pointer) {
    if (!pointer) {
        return;
    }

    auto *bufPointer = (std::vector<char> *) (intptr_t) pointer;
    delete bufPointer;
}

extern "C" JNIEXPORT jstring
Java_dev_ragnarok_fenrir_module_StringHash_getSha1(JNIEnv *env, jobject, jstring value) {
    char const *textString = SafeGetStringUTFChars(env, value, nullptr);
    if (!textString) {
        return env->NewStringUTF("error");
    }
    string v = textString;
    env->ReleaseStringUTFChars(value, textString);
    return env->NewStringUTF(SHA1::from_string(v).c_str());
}

extern "C" JNIEXPORT jstring
Java_dev_ragnarok_fenrir_module_StringHash_getSha1ByteArray(JNIEnv *env, jobject,
                                                            jbyteArray value) {
    jboolean isCopy;
    jint length = env->GetArrayLength(value);
    jbyte *buf = env->GetByteArrayElements(value, &isCopy);

    if (!buf) {
        return env->NewStringUTF("error");
    }
    string v;
    v.resize(length);
    memcpy((char *) v.data(), buf, length);
    env->ReleaseByteArrayElements(value, buf, JNI_ABORT);
    return env->NewStringUTF(SHA1::from_string(v).c_str());
}

extern "C" JNIEXPORT jint
Java_dev_ragnarok_fenrir_module_StringHash_getCRC32(JNIEnv *env, jobject,
                                                    jstring value) {
    char const *textString = SafeGetStringUTFChars(env, value, nullptr);
    if (!textString) {
        return 0;
    }
    string v = textString;
    env->ReleaseStringUTFChars(value, textString);
    return crc32(v);
}