/**
 * Copyright 2016 Ezhome Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy from the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yoloo.android.rxfirebase.exception;

/**
 * Raised when the supplied auth token has expired
 */
public class FirebaseExpiredTokenException extends Exception {

  public FirebaseExpiredTokenException() {
    super();
  }

  public FirebaseExpiredTokenException(String detailMessage) {
    super(detailMessage);
  }

  public FirebaseExpiredTokenException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public FirebaseExpiredTokenException(Throwable throwable) {
    super(throwable);
  }
}
