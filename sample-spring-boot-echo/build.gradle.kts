/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

apply(plugin = "org.springframework.boot")

dependencies {
    implementation(project(":line-bot-spring-boot"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
    implementation("com.fasterxml.jackson.core:jackson-core:2.11.2")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.2")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-annotations
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.11.2")

    // https://mvnrepository.com/artifact/org.jsoup/jsoup
    implementation("org.jsoup:jsoup:1.13.1")

    // https://mvnrepository.com/artifact/com.atilika.kuromoji/kuromoji-core
    implementation("com.atilika.kuromoji:kuromoji-core:0.9.0")

    // https://mvnrepository.com/artifact/com.atilika.kuromoji/kuromoji-ipadic
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
