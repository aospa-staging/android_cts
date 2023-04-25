/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.inputmethodservice.cts.ime;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * A utility class that represents A8R8G8B bitmap as an integer array.
 */
final class BitmapImage {
    /**
     * Tolerance level between the expected color and the actual color in each color channel.
     *
     * <p>See Bug 174534092 about why we ended up having this.</p>
     */
    private static final int TOLERANCE = 4;

    @NonNull
    private final int[] mPixels;
    private final int mWidth;
    private final int mHeight;

    BitmapImage(@NonNull int[] pixels, int width, int height) {
        mWidth = width;
        mHeight = height;
        mPixels = pixels;
    }

    /**
     * Create {@link BitmapImage} from {@link Bitmap}.
     *
     * @param bitmap {@link Bitmap} from which {@link BitmapImage} will be created.
     * @return A new instance of {@link BitmapImage}.
     */
    @AnyThread
    static BitmapImage createFromBitmap(@NonNull Bitmap bitmap) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return new BitmapImage(pixels, width, height);
    }

    /**
     * @return Width of this image.
     */
    @AnyThread
    int getWidth() {
        return mWidth;
    }

    /**
     * @return Height of this image.
     */
    @AnyThread
    int getHeight() {
        return mHeight;
    }

    /**
     * @return {@link Bitmap} that has the same pixel patterns.
     */
    @AnyThread
    @NonNull
    Bitmap toBitmap() {
        return Bitmap.createBitmap(mPixels, mWidth, mHeight, Bitmap.Config.ARGB_8888);
    }

    /**
     * Returns pixel color in A8R8G8B8 format.
     *
     * @param x X coordinate of the pixel.
     * @param y Y coordinate of the pixel.
     * @return Pixel color in A8R8G8B8 format.
     */
    @ColorInt
    @AnyThread
    int getPixel(int x, int y) {
        return mPixels[y * mWidth + x];
    }

    /**
     * Compares two given pixels to determine whether those two pixels are considered to be
     * the same within {@link #TOLERANCE}.
     *
     * @param lhs a color integer to be compared.
     * @param rhs another color integer to be compared.
     * @return {@true} if two given pixels are the same within {@link #TOLERANCE}.
     */
    private static boolean robustMatchInternal(@ColorInt int lhs, @ColorInt int rhs) {
        return lhs == rhs || (Math.abs(Color.red(lhs) - Color.red(rhs)) <= TOLERANCE
                && Math.abs(Color.green(lhs) - Color.green(rhs)) <= TOLERANCE
                && Math.abs(Color.blue(lhs) - Color.blue(rhs)) <= TOLERANCE);
    }

    /**
     * Checks if the same image can be found in the specified {@link BitmapImage} within a certain
     * error margin.
     *
     * @param targetImage {@link BitmapImage} to be checked.
     * @param offsetX X offset in the {@code targetImage} used when comparing.
     * @param offsetY Y offset in the {@code targetImage} used when comparing.
     * @return
     */
    @AnyThread
    boolean robustMatch(@NonNull BitmapImage targetImage, int offsetX, int offsetY) {
        final int targetWidth = targetImage.getWidth();
        final int targetHeight = targetImage.getHeight();

        for (int y = 0; y < mHeight; ++y) {
            for (int x = 0; x < mWidth; ++x) {
                final int targetX = x + offsetX;
                if (targetX < 0 || targetWidth <= targetX) {
                    return false;
                }
                final int targetY = y + offsetY;
                if (targetY < 0 || targetHeight <= targetY) {
                    return false;
                }
                if (!robustMatchInternal(targetImage.getPixel(targetX, targetY), getPixel(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }
}
