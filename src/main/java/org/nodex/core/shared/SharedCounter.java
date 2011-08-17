/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.shared;

import org.cliffc.high_scale_lib.ConcurrentAutoTable;

public class SharedCounter implements Counter {

  private final ConcurrentAutoTable counter = new ConcurrentAutoTable();

  public void add(long amount) {
    counter.add(amount);
  }

  public void subtract(long amount) {
    counter.add(-amount);
  }

  public void increment() {
    counter.increment();
  }

  public void decrement() {
    counter.decrement();
  }

  public void set(long value) {
    counter.set(value);
  }

  public long get() {
    return counter.get();
  }
}
