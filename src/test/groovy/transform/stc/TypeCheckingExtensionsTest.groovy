/*
 * Copyright 2003-2012 the original author or authors.
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
package groovy.transform.stc

import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

/**
 * Units tests for type checking extensions.
 *
 * @author Cedric Champeau
 */
class TypeCheckingExtensionsTest extends StaticTypeCheckingTestCase {

    private void setExtension(String name) {
        def cz = config.compilationCustomizers.find {
            it instanceof ASTTransformationCustomizer
        }
        if (name) {
            cz.annotationParameters = [extensions: name]
        } else {
            cz.annotationParameters = [:]
        }
    }

    void testSetupExtension() {
        extension = 'groovy/transform/stc/SetupTestExtension.groovy'
        assertScript '''
            @ASTTest(phase=INSTRUCTION_SELECTION, value={
                assert node.getNodeMetaData('setup')
            })
            class A {}
            @ASTTest(phase=INSTRUCTION_SELECTION, value={
                assert node.getNodeMetaData('setup') == null
            })
            class B {}
            new A()
        '''
    }

    void testFinishExtension() {
        extension = 'groovy/transform/stc/FinishTestExtension.groovy'
        assertScript '''
            @ASTTest(phase=INSTRUCTION_SELECTION, value={
                assert node.getNodeMetaData('finish')
            })
            class A {}
            @ASTTest(phase=INSTRUCTION_SELECTION, value={
                assert node.getNodeMetaData('finish') == null
            })
            class B {}
            new A()
        '''
    }

    void testUndefinedVariable() {
        extension = 'groovy/transform/stc/UndefinedVariableTestExtension.groovy'
        try {
            assertScript '''
                foo.toUpperCase() // normal type checker would fail here
            '''
        } catch (MissingPropertyException e) {
            // normal
        }
    }

    void testUndefinedVariableNoHandle() {
        extension = 'groovy/transform/stc/UndefinedVariableNoHandleTestExtension.groovy'
        shouldFailWithMessages '''
                foo.toUpperCase() // normal type checker would fail here
            ''', 'The variable [foo] is undeclared'
    }

    void testMissingMethod() {
        extension = null
        shouldFailWithMessages '''
            String msg = 'foo'
            msg.TOUPPERCASE()
        ''', 'Cannot find matching method'
        extension = 'groovy/transform/stc/MissingMethod1TestExtension.groovy'
        try {
            assertScript '''
                String msg = 'foo'
                msg.TOUPPERCASE()
            '''
        } catch (MissingMethodException e) {
            // normal
        }
    }

    void testMissingMethodWithLogic() {
        extension = null
        shouldFailWithMessages '''
            String msg = 'foo'
            msg.SIZE()
            msg.CONCAT('bar')
        ''', 'Cannot find matching method java.lang.String#SIZE()', 'Cannot find matching method java.lang.String#CONCAT(java.lang.String)'
        extension = 'groovy/transform/stc/MissingMethod2TestExtension.groovy'
        try {
            assertScript '''
                String msg = 'foo'
                msg.SIZE()
                msg.CONCAT('bar')
            '''
        } catch (MissingMethodException e) {
            // normal
        }
    }

    void testShouldSilenceTypeChecker() {
        extension = 'groovy/transform/stc/SilentTestExtension.groovy'
        assertScript '''import org.codehaus.groovy.runtime.typehandling.GroovyCastException
            try {
                int x = 'foo'
            } catch (GroovyCastException e) {
            }
        '''
    }

    void testShouldChangeErrorPrefix() {
        extension = 'groovy/transform/stc/PrefixChangerTestExtension.groovy'
        shouldFailWithMessages '''
           int x = 'foo'
        ''', '[Custom] - Cannot assign value of type java.lang.String to variable of type int'
    }

    void testAfterMethodCallHook() {
        extension = 'groovy/transform/stc/SprintfExtension.groovy'
        shouldFailWithMessages '''
            String count = 'foo'
            sprintf("Count = %d", count)
        ''', 'Parameter types didn\'t match types expected from the format String',
                'For placeholder 1 [%d] expected \'int\' but was \'java.lang.String\''
    }

    void testBeforeMethodCallHook() {
        extension = 'groovy/transform/stc/UpperCaseMethodTest1Extension.groovy'
        shouldFailWithMessages '''
            String method() { 'foo' }
            String BOO() { 'bar' }
            method() // ok
            BOO() // error
        ''', 'Calling a method which is all uppercase is not allowed'
    }

    void testBeforeMethodHook() {
        extension = 'groovy/transform/stc/UpperCaseMethodTest2Extension.groovy'
        shouldFailWithMessages '''
            String method() { 'foo' } // ok
            String BOO() { 'bar' } // error
        ''', 'Defining method which is all uppercase is not allowed'
    }

    void testAfterMethodHook() {
        extension = 'groovy/transform/stc/UpperCaseMethodTest3Extension.groovy'
        shouldFailWithMessages '''
            String method() { 'foo' } // ok
            String BOO() { 'bar' } // error
        ''', 'Defining method which is all uppercase is not allowed'
    }

    void testMethodSelection() {
        // first step checks that without extension, type checking works properly
        extension = null
        assertScript '''
        @ASTTest(phase=INSTRUCTION_SELECTION, value={
            assert node.getNodeMetaData('selected') == null
        })
        def str = 'foo'.toUpperCase()
        '''

        // then we use a type checking extension, we add node metadata
        extension = 'groovy/transform/stc/OnMethodSelectionTestExtension.groovy'
        assertScript '''
        @ASTTest(phase=INSTRUCTION_SELECTION, value={
            assert node.getNodeMetaData('selected') == true
        })
        def str = 'foo'.toUpperCase()
        '''
    }

    void testUnresolvedProperty() {
        extension = null
        shouldFailWithMessages '''
            'str'.FOO
        ''', 'No such property: FOO for class: java.lang.String'

        extension = 'groovy/transform/stc/UnresolvedPropertyTestExtension.groovy'
        assertScript '''
            try {
                'str'.FOO
            } catch (MissingPropertyException ex) {
            }
        '''
    }

    void testUnresolvedAttribute() {
        extension = null
        shouldFailWithMessages '''
            'str'.@FOO
        ''', 'No such property: FOO for class: java.lang.String'

        extension = 'groovy/transform/stc/UnresolvedAttributeTestExtension.groovy'
        assertScript '''
            try {
                'str'.@FOO
            } catch (MissingFieldException ex) {
            }
        '''
    }
}
