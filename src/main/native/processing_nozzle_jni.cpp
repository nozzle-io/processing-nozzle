#include <jni.h>

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <new>
#include <string>

#include "nozzle/nozzle_c.h"

namespace {

thread_local std::string last_error;

constexpr int status_pass = 0;
constexpr int status_fail = 1;
constexpr int status_missing_host_smoke = 2;
constexpr int status_unsupported = 3;

struct sender_handle {
    NozzleSender *sender{nullptr};
};

struct receiver_handle {
    NozzleReceiver *receiver{nullptr};
};

void set_last_error(const std::string &message) {
    last_error = message;
}

std::string error_text(const char *prefix, NozzleErrorCode code) {
    return std::string(prefix) + ": NOZZLE_ERROR_CODE=" + std::to_string(static_cast<int>(code));
}

int status_from_error(NozzleErrorCode code, const char *prefix) {
    if (code == NOZZLE_OK) {
        set_last_error("OK");
        return status_pass;
    }
    set_last_error(error_text(prefix, code));
    switch (code) {
        case NOZZLE_ERROR_UNSUPPORTED_BACKEND:
        case NOZZLE_ERROR_UNSUPPORTED_FORMAT:
        case NOZZLE_ERROR_RESOURCE_CREATION_FAILED:
        case NOZZLE_ERROR_SHARED_HANDLE_FAILED:
        case NOZZLE_ERROR_SENDER_NOT_FOUND:
        case NOZZLE_ERROR_TIMEOUT:
        case NOZZLE_ERROR_BACKEND_ERROR:
            return status_missing_host_smoke;
        case NOZZLE_ERROR_INVALID_ARGUMENT:
        case NOZZLE_ERROR_DEVICE_MISMATCH:
        case NOZZLE_ERROR_SENDER_CLOSED:
        case NOZZLE_ERROR_COMMAND_FAILED:
        case NOZZLE_ERROR_UNKNOWN:
        default:
            return status_fail;
    }
}

std::string string_from_jstring(JNIEnv *env, jstring text) {
    if (!text) {
        return std::string();
    }
    const char *chars = env->GetStringUTFChars(text, nullptr);
    if (!chars) {
        return std::string();
    }
    std::string result{chars};
    env->ReleaseStringUTFChars(text, chars);
    return result;
}

sender_handle *to_sender(jlong handle) {
    return reinterpret_cast<sender_handle *>(static_cast<intptr_t>(handle));
}

receiver_handle *to_receiver(jlong handle) {
    return reinterpret_cast<receiver_handle *>(static_cast<intptr_t>(handle));
}

jlong to_jlong(sender_handle *handle) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

jlong to_jlong(receiver_handle *handle) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(handle));
}

bool valid_dimensions(jint width, jint height) {
    return 0 < width && 0 < height && width <= 16384 && height <= 16384;
}

bool checked_row_stride(const NozzleMappedPixels &pixels, uint32_t required_row_bytes, int64_t *out_stride) {
    if (!pixels.data || !out_stride) {
        set_last_error("mapped pixels missing data");
        return false;
    }
    if (pixels.row_stride_bytes == std::numeric_limits<int64_t>::min()) {
        set_last_error("mapped row stride is INT64_MIN");
        return false;
    }
    int64_t abs_stride = pixels.row_stride_bytes < 0 ? -pixels.row_stride_bytes : pixels.row_stride_bytes;
    if (abs_stride < static_cast<int64_t>(required_row_bytes)) {
        set_last_error("mapped row stride is smaller than required row bytes");
        return false;
    }
    *out_stride = pixels.row_stride_bytes;
    return true;
}

uint8_t *mapped_row(NozzleMappedPixels &pixels, uint32_t y, int64_t stride) {
    uint8_t *base = static_cast<uint8_t *>(pixels.data);
    if (0 <= stride) {
        return base + static_cast<size_t>(y) * static_cast<size_t>(stride);
    }
    int64_t abs_stride = -stride;
    return base - static_cast<size_t>(y) * static_cast<size_t>(abs_stride);
}

void copy_argb_to_rgba(JNIEnv *env, jintArray argb_pixels, NozzleMappedPixels &pixels, jint width, jint height) {
    jint *argb = env->GetIntArrayElements(argb_pixels, nullptr);
    if (!argb) {
        set_last_error("GetIntArrayElements failed");
        return;
    }

    int64_t stride = 0;
    if (!checked_row_stride(pixels, static_cast<uint32_t>(width) * 4u, &stride)) {
        env->ReleaseIntArrayElements(argb_pixels, argb, JNI_ABORT);
        return;
    }

    for (jint y = 0; y < height; y++) {
        uint8_t *row = mapped_row(pixels, static_cast<uint32_t>(y), stride);
        for (jint x = 0; x < width; x++) {
            uint32_t value = static_cast<uint32_t>(argb[y * width + x]);
            size_t offset = static_cast<size_t>(x) * 4u;
            row[offset + 0u] = static_cast<uint8_t>((value >> 16u) & 0xffu);
            row[offset + 1u] = static_cast<uint8_t>((value >> 8u) & 0xffu);
            row[offset + 2u] = static_cast<uint8_t>(value & 0xffu);
            row[offset + 3u] = static_cast<uint8_t>((value >> 24u) & 0xffu);
        }
    }

    env->ReleaseIntArrayElements(argb_pixels, argb, JNI_ABORT);
    set_last_error("OK");
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeVersion(JNIEnv *env, jclass) {
    std::string text = "processing-nozzle-jni; nozzle_c_error_ok=" + std::to_string(static_cast<int>(NOZZLE_OK))
        + "; rgba8=" + std::to_string(static_cast<int>(NOZZLE_FORMAT_RGBA8_UNORM));
    return env->NewStringUTF(text.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeBackendDiagnostics(JNIEnv *env, jclass) {
    NozzleBackendCapabilities caps{};
    caps.struct_size = sizeof(NozzleBackendCapabilities);
    NozzleErrorCode caps_code = nozzle_get_backend_capabilities(NOZZLE_BACKEND_DMA_BUF, &caps);
    std::string text = "dma_buf_available=" + std::to_string(nozzle_backend_is_available(NOZZLE_BACKEND_DMA_BUF))
        + "; caps_code=" + std::to_string(static_cast<int>(caps_code))
        + "; capability_flags=" + std::to_string(caps.capability_flags)
        + "; sharing_mechanisms=" + std::to_string(caps.sharing_mechanisms)
        + "; cpu_read_bits=" + std::to_string(caps.cpu_read_format_bits)
        + "; cpu_write_bits=" + std::to_string(caps.cpu_write_format_bits);
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeSelfTest(JNIEnv *, jclass) {
    if (NOZZLE_OK != 0) {
        return 1;
    }
    if (NOZZLE_FORMAT_RGBA8_UNORM != 4) {
        return 2;
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeLastError(JNIEnv *env, jclass) {
    return env->NewStringUTF(last_error.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeCreateSender(JNIEnv *env, jclass, jstring name) {
    std::string native_name_storage = string_from_jstring(env, name);
    const char *native_name = native_name_storage.c_str();
    if (!native_name[0]) {
        set_last_error("sender name is empty");
        return 0;
    }

    NozzleSenderDesc desc{};
    desc.name = native_name;
    desc.application_name = "processing-nozzle";
    desc.ring_buffer_size = 3;
    desc.fallback_flags_valid = 1;
    desc.fallback_flags = NOZZLE_FALLBACK_SAFE_DEFAULTS;

    NozzleSender *sender = nullptr;
    NozzleErrorCode code = nozzle_sender_create(&desc, &sender);
    if (code != NOZZLE_OK || !sender) {
        status_from_error(code, "nozzle_sender_create");
        return 0;
    }

    sender_handle *handle = new (std::nothrow) sender_handle{};
    if (!handle) {
        nozzle_sender_destroy(sender);
        set_last_error("sender handle allocation failed");
        return 0;
    }
    handle->sender = sender;
    set_last_error("OK");
    return to_jlong(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeDestroySender(JNIEnv *, jclass, jlong raw_handle) {
    sender_handle *handle = to_sender(raw_handle);
    if (!handle) {
        return;
    }
    nozzle_sender_destroy(handle->sender);
    delete handle;
    set_last_error("OK");
}

extern "C" JNIEXPORT jint JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativePublishArgbPixels(JNIEnv *env, jclass, jlong raw_handle, jintArray argb_pixels, jint width, jint height) {
    sender_handle *handle = to_sender(raw_handle);
    if (!handle || !handle->sender || !argb_pixels || !valid_dimensions(width, height)) {
        set_last_error("invalid publish arguments");
        return status_fail;
    }
    jsize length = env->GetArrayLength(argb_pixels);
    if (length < width * height) {
        set_last_error("argb array is smaller than width*height");
        return status_fail;
    }

    NozzleFrame *frame = nullptr;
    NozzleErrorCode code = nozzle_sender_acquire_writable_frame(
        handle->sender,
        static_cast<uint32_t>(width),
        static_cast<uint32_t>(height),
        NOZZLE_FORMAT_RGBA8_UNORM,
        &frame
    );
    if (code != NOZZLE_OK || !frame) {
        return status_from_error(code, "nozzle_sender_acquire_writable_frame");
    }

    NozzlePixelMapping *mapping = nullptr;
    NozzleMappedPixels pixels{};
    code = nozzle_frame_lock_writable_pixels_mapping_with_origin(frame, NOZZLE_ORIGIN_TOP_LEFT, &mapping, &pixels);
    if (code != NOZZLE_OK || !mapping) {
        (void)nozzle_sender_discard_frame(handle->sender, frame);
        nozzle_frame_release(frame);
        return status_from_error(code, "nozzle_frame_lock_writable_pixels_mapping_with_origin");
    }

    copy_argb_to_rgba(env, argb_pixels, pixels, width, height);
    bool copy_ok = last_error == "OK";

    NozzleErrorCode unlock_code = nozzle_pixel_mapping_unlock_checked(&mapping);
    if (!copy_ok) {
        (void)nozzle_sender_discard_frame(handle->sender, frame);
        nozzle_frame_release(frame);
        return status_fail;
    }
    if (unlock_code != NOZZLE_OK) {
        (void)nozzle_sender_discard_frame(handle->sender, frame);
        nozzle_frame_release(frame);
        return status_from_error(unlock_code, "nozzle_pixel_mapping_unlock_checked");
    }

    code = nozzle_sender_commit_frame(handle->sender, frame);
    nozzle_frame_release(frame);
    return status_from_error(code, "nozzle_sender_commit_frame");
}

extern "C" JNIEXPORT jlong JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeCreateReceiver(JNIEnv *env, jclass, jstring source_name) {
    std::string native_source_storage = string_from_jstring(env, source_name);
    const char *native_source = native_source_storage.c_str();
    if (!native_source[0]) {
        set_last_error("source name is empty");
        return 0;
    }

    NozzleReceiverDesc desc{};
    desc.name = native_source;
    desc.application_name = "processing-nozzle";
    desc.receive_mode = NOZZLE_RECEIVE_LATEST_ONLY;

    NozzleReceiver *receiver = nullptr;
    NozzleErrorCode code = nozzle_receiver_create(&desc, &receiver);
    if (code != NOZZLE_OK || !receiver) {
        status_from_error(code, "nozzle_receiver_create");
        return 0;
    }

    receiver_handle *handle = new (std::nothrow) receiver_handle{};
    if (!handle) {
        nozzle_receiver_destroy(receiver);
        set_last_error("receiver handle allocation failed");
        return 0;
    }
    handle->receiver = receiver;
    set_last_error("OK");
    return to_jlong(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeDestroyReceiver(JNIEnv *, jclass, jlong raw_handle) {
    receiver_handle *handle = to_receiver(raw_handle);
    if (!handle) {
        return;
    }
    nozzle_receiver_destroy(handle->receiver);
    delete handle;
    set_last_error("OK");
}

extern "C" JNIEXPORT jint JNICALL
Java_processing_nozzle_ProcessingNozzleNative_nativeReceiveRgba(JNIEnv *env, jclass, jlong raw_handle, jint width, jint height, jbyteArray out_rgba, jlong timeout_ms) {
    receiver_handle *handle = to_receiver(raw_handle);
    if (!handle || !handle->receiver || !out_rgba || !valid_dimensions(width, height) || timeout_ms < 0) {
        set_last_error("invalid receive arguments");
        return status_fail;
    }
    uint64_t required_size = static_cast<uint64_t>(width) * static_cast<uint64_t>(height) * 4u;
    if (static_cast<uint64_t>(env->GetArrayLength(out_rgba)) < required_size) {
        set_last_error("output RGBA array is smaller than width*height*4");
        return status_fail;
    }

    NozzleAcquireDesc desc{};
    desc.timeout_ms = static_cast<uint64_t>(timeout_ms);
    NozzleFrame *frame = nullptr;
    NozzleErrorCode code = nozzle_receiver_acquire_frame(handle->receiver, &desc, &frame);
    if (code != NOZZLE_OK || !frame) {
        return status_from_error(code, "nozzle_receiver_acquire_frame");
    }

    NozzleFrameInfo info{};
    code = nozzle_frame_get_info(frame, &info);
    if (code != NOZZLE_OK) {
        nozzle_frame_release(frame);
        return status_from_error(code, "nozzle_frame_get_info");
    }
    if (info.width != static_cast<uint32_t>(width) || info.height != static_cast<uint32_t>(height)) {
        nozzle_frame_release(frame);
        set_last_error("received frame size mismatch");
        return status_fail;
    }

    jbyte *out = env->GetByteArrayElements(out_rgba, nullptr);
    if (!out) {
        nozzle_frame_release(frame);
        set_last_error("GetByteArrayElements failed");
        return status_fail;
    }

    NozzleMappedPixels copied{};
    code = nozzle_frame_copy_pixels_with_origin(
        frame,
        NOZZLE_ORIGIN_TOP_LEFT,
        out,
        required_size,
        &copied
    );
    env->ReleaseByteArrayElements(out_rgba, out, code == NOZZLE_OK ? 0 : JNI_ABORT);
    nozzle_frame_release(frame);
    return status_from_error(code, "nozzle_frame_copy_pixels_with_origin");
}
