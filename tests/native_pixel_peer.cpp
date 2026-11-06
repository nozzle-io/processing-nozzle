#include <jni.h>
#include <nozzle/nozzle_c.h>
#include <cstdint>
#include <cstdio>
#include <string>

namespace {
struct resources {
    NozzleSender *sender = nullptr;
    NozzleReceiver *receiver = nullptr;
    NozzleFrame *frame = nullptr;
    NozzlePixelMapping *mapping = nullptr;
    bool committed = false;
    ~resources() {
        nozzle_pixel_mapping_unlock(&mapping);
        if(frame) {
            if(sender && !committed) (void)nozzle_sender_discard_frame(sender, frame);
            nozzle_frame_release(frame);
        }
        if(receiver) nozzle_receiver_destroy(receiver);
        if(sender) nozzle_sender_destroy(sender);
    }
};

std::string name_from_java(JNIEnv *env, jstring name) {
    const char *chars = env->GetStringUTFChars(name, nullptr);
    if(!chars) return {};
    std::string text(chars);
    env->ReleaseStringUTFChars(name, chars);
    return text;
}

bool ok(NozzleErrorCode code, const char *operation) {
    if(code == NOZZLE_OK) return true;
    std::fprintf(stderr, "%s error=%d\n", operation, static_cast<int>(code));
    return false;
}

bool storage(const NozzleMappedPixels &pixels, int width, int height) {
    if(!pixels.data || pixels.format != NOZZLE_FORMAT_BGRA8_UNORM ||
       pixels.width != static_cast<uint32_t>(width) || pixels.height != static_cast<uint32_t>(height) ||
       pixels.row_stride_bytes < static_cast<int64_t>(width) * 4) {
        std::fprintf(stderr, "required native BGRA8 mapping unavailable, no SKIP\n");
        return false;
    }
    std::printf("native BGRA8 %dx%d stride=%lld\n", width, height, (long long)pixels.row_stride_bytes);
    return true;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_processing_nozzle_NativePixelPeer_check(JNIEnv *env, jclass, jstring name, jint width, jint height) {
    resources peer;
    const std::string sender_name = name_from_java(env, name);
    NozzleReceiverDesc desc{};
    desc.name = sender_name.c_str();
    desc.receive_mode = NOZZLE_RECEIVE_LATEST_ONLY;
    if(!ok(nozzle_receiver_create(&desc, &peer.receiver), "native receiver create")) return false;
    NozzleAcquireDesc acquire{};
    acquire.timeout_ms = 3000;
    if(!ok(nozzle_receiver_acquire_frame(peer.receiver, &acquire, &peer.frame), "native acquire")) return false;
    NozzleMappedPixels pixels{};
    if(!ok(nozzle_frame_lock_pixels_mapping_with_origin(peer.frame, NOZZLE_ORIGIN_TOP_LEFT,
        &peer.mapping, &pixels), "native read map") || !storage(pixels, width, height)) return false;
    // Independent storage oracle: no production Processing conversion is called.
    for(int y = 0; y < height; ++y) {
        const auto *row = static_cast<const uint8_t *>(pixels.data) + y * pixels.row_stride_bytes;
        for(int x = 0; x < width; ++x) {
            const int expected[] = {40 + y % 79, 20 + x % 193, 190 + y % 31, 149 + x % 73};
            for(int c = 0; c < 4; ++c) {
                if(row[4*x+c] != expected[c]) {
                    std::fprintf(stderr, "native receive x=%d y=%d channel=%d actual=%u expected=%d\n",
                        x, y, c, row[4*x+c], expected[c]);
                    return false;
                }
            }
        }
    }
    return ok(nozzle_pixel_mapping_unlock_checked(&peer.mapping), "native read unlock");
}

extern "C" JNIEXPORT jlong JNICALL
Java_processing_nozzle_NativePixelPeer_publish(JNIEnv *env, jclass, jstring name, jint width, jint height) {
    resources peer;
    const std::string sender_name = name_from_java(env, name);
    NozzleSenderDesc desc{};
    desc.name = sender_name.c_str();
    desc.application_name = "processing native peer";
    desc.ring_buffer_size = 3;
    desc.fallback_flags_valid = 1;
    desc.fallback_flags = NOZZLE_FALLBACK_SAFE_DEFAULTS;
    if(!ok(nozzle_sender_create(&desc, &peer.sender), "native sender create")) return 0;
    NozzleSenderInfoSnapshot info{};
    if(!ok(nozzle_sender_get_info_snapshot(peer.sender, &info), "native sender info") ||
       info.backend != NOZZLE_BACKEND_METAL) {
        std::fprintf(stderr, "Metal backend required, no SKIP\n");
        return 0;
    }
    if(!ok(nozzle_sender_acquire_writable_frame(peer.sender, width, height,
        NOZZLE_FORMAT_RGBA8_UNORM, &peer.frame), "native writable acquire")) return 0;
    NozzleMappedPixels pixels{};
    if(!ok(nozzle_frame_lock_writable_pixels_mapping_with_origin(peer.frame, NOZZLE_ORIGIN_TOP_LEFT,
        &peer.mapping, &pixels), "native write map") || !storage(pixels, width, height)) return 0;
    // Independent native producer: write actual BGRA lanes directly.
    for(int y = 0; y < height; ++y) {
        auto *row = static_cast<uint8_t *>(pixels.data) + y * pixels.row_stride_bytes;
        for(int x = 0; x < width; ++x) {
            row[4*x] = 40 + y % 79;
            row[4*x+1] = 20 + x % 193;
            row[4*x+2] = 190 + y % 31;
            row[4*x+3] = 149 + x % 73;
        }
    }
    if(!ok(nozzle_pixel_mapping_unlock_checked(&peer.mapping), "native write unlock") ||
       !ok(nozzle_sender_commit_frame(peer.sender, peer.frame), "native commit")) return 0;
    peer.committed = true;
    NozzleSender *sender = peer.sender;
    peer.sender = nullptr;
    return static_cast<jlong>(reinterpret_cast<intptr_t>(sender));
}

extern "C" JNIEXPORT void JNICALL
Java_processing_nozzle_NativePixelPeer_destroy(JNIEnv *, jclass, jlong sender) {
    nozzle_sender_destroy(reinterpret_cast<NozzleSender *>(static_cast<intptr_t>(sender)));
}
