/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.debugger;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.JetTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@RunWith(JUnit3RunnerWithInners.class)
public class JetPositionManagerTestGenerated extends AbstractJetPositionManagerTest {
    @TestMetadata("idea/testData/debugger/positionManager")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class SingleFile extends AbstractJetPositionManagerTest {
        public void testAllFilesPresentInSingleFile() throws Exception {
            JetTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/debugger/positionManager"), Pattern.compile("^(.+)\\.kt$"), false);
        }

        @TestMetadata("class.kt")
        public void testClass() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/class.kt");
            doTest(fileName);
        }

        @TestMetadata("enum.kt")
        public void testEnum() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/enum.kt");
            doTest(fileName);
        }

        @TestMetadata("extensionFunction.kt")
        public void testExtensionFunction() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/extensionFunction.kt");
            doTest(fileName);
        }

        @TestMetadata("innerClass.kt")
        public void testInnerClass() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/innerClass.kt");
            doTest(fileName);
        }

        @TestMetadata("objectDeclaration.kt")
        public void testObjectDeclaration() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/objectDeclaration.kt");
            doTest(fileName);
        }

        @TestMetadata("package.kt")
        public void testPackage() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/package.kt");
            doTest(fileName);
        }

        @TestMetadata("propertyAccessor.kt")
        public void testPropertyAccessor() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/propertyAccessor.kt");
            doTest(fileName);
        }

        @TestMetadata("propertyInitializer.kt")
        public void testPropertyInitializer() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/propertyInitializer.kt");
            doTest(fileName);
        }

        @TestMetadata("topLevelPropertyInitializer.kt")
        public void testTopLevelPropertyInitializer() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/topLevelPropertyInitializer.kt");
            doTest(fileName);
        }

        @TestMetadata("trait.kt")
        public void testTrait() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/trait.kt");
            doTest(fileName);
        }

        @TestMetadata("twoClasses.kt")
        public void testTwoClasses() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/twoClasses.kt");
            doTest(fileName);
        }

        @TestMetadata("_DefaultPackage.kt")
        public void test_DefaultPackage() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/_DefaultPackage.kt");
            doTest(fileName);
        }
    }

    @TestMetadata("idea/testData/debugger/positionManager")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class MultiFile extends AbstractJetPositionManagerTest {
        public void testAllFilesPresentInMultiFile() throws Exception {
            JetTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("idea/testData/debugger/positionManager"), Pattern.compile("^([^\\.]+)$"), false);
        }

        @TestMetadata("multiFilePackage")
        public void testMultiFilePackage() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/multiFilePackage/");
            doTest(fileName);
        }

        @TestMetadata("multiFileSameName")
        public void testMultiFileSameName() throws Exception {
            String fileName = JetTestUtils.navigationMetadata("idea/testData/debugger/positionManager/multiFileSameName/");
            doTest(fileName);
        }
    }
}
