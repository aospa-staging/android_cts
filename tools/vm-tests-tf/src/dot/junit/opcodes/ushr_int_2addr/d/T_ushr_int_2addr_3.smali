# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

.source "T_ushr_int_2addr_3.java"
.class  public Ldot/junit/opcodes/ushr_int_2addr/d/T_ushr_int_2addr_3;
.super  Ljava/lang/Object;

.method public constructor <init>()V
.registers 1
       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(IJ)I
.registers 4

       ushr-int/2addr v1, v2
       return v1
.end method


