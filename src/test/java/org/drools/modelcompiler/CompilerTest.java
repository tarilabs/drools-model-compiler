/*
 * Copyright 2005 JBoss Inc
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

package org.drools.modelcompiler;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieBuilderImpl;
import org.drools.modelcompiler.builder.CanonicalModelKieProject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class CompilerTest {
    
    public static enum RUN_TYPE {
        USE_CANONICAL_MODEL,
        STANDARD_FROM_DRL;
    }
    
    @Parameters(name = "{0}")
    public static Object[] params() {
        return new Object[]{
                            RUN_TYPE.STANDARD_FROM_DRL,
                            RUN_TYPE.USE_CANONICAL_MODEL
        };
    }

    private final RUN_TYPE testRunType;
    
    public CompilerTest(RUN_TYPE testRunType) {
        this.testRunType = testRunType;
    }

    public static class Result {
        private Object value;

        public Object getValue() {
            return value;
        }

        public void setValue( Object value ) {
            this.value = value;
        }
    }

    @Test
    public void testBeta() {
        String str =
                "import " + Result.class.getCanonicalName() + ";" +
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $r : Result()\n" +
                "  $p1 : Person(name == \"Mark\")\n" +
                "  $p2 : Person(name != \"Mark\", age > $p1.age)\n" +
                "then\n" +
                "  $r.setValue($p2.getName() + \" is older than \" + $p1.getName());\n" +
                "end";

        KieSession ksession = getKieSession( str );

        Result result = new Result();
        ksession.insert(result);

        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.insert(new Person("Mario", 40));
        ksession.fireAllRules();

        assertEquals( "Mario is older than Mark", result.getValue() );
    }

    @Test
    public void test3Patterns() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $mark : Person(name == \"Mark\")\n" +
                "  $p : Person(age > $mark.age)\n" +
                "  $s: String(this == $p.name)\n" +
                "then\n" +
                "  System.out.println(\"Found: \" + $s);\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( "Mario" );
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.insert(new Person("Mario", 40));
        ksession.fireAllRules();
    }

    @Test
    public void testOr() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $p : Person(name == \"Mark\") or\n" +
                "  ( $mark : Person(name == \"Mark\")\n" +
                "    and\n" +
                "    $p : Person(age > $mark.age) )\n" +
                "  $s: String(this == $p.name)\n" +
                "then\n" +
                "  System.out.println(\"Found: \" + $s);\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( "Mario" );
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Edson", 35));
        ksession.insert(new Person("Mario", 40));
        ksession.fireAllRules();
    }

    private KieSession getKieSession(String str) {
        KieServices ks = KieServices.Factory.get();
        
        ReleaseId releaseId = ks.newReleaseId( "org.kie", "kjar-test-" + UUID.randomUUID(), "1.0" );
        
        KieRepository repo = ks.getRepository();
        repo.removeKieModule( releaseId );
        
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.writePomXML(KJARUtils.getPom(releaseId));
        kfs.write( "src/main/resources/r1.drl", str );
// This is actually taken from classloader of test (?) - or anyway it must, because the test are instantiating directly Person.
//        String javaSrc = Person.class.getCanonicalName().replace( '.', File.separatorChar ) + ".java";
//        Resource javaResource = ks.getResources().newFileSystemResource( "src/test/java/" + javaSrc );
//        kfs.write( "src/main/java/" + javaSrc, javaResource );

        KieBuilder kieBuilder = (testRunType == RUN_TYPE.USE_CANONICAL_MODEL) ?
                                ( (KieBuilderImpl) ks.newKieBuilder( kfs ) ).buildAll( CanonicalModelKieProject::new ) :
                                ks.newKieBuilder( kfs ).buildAll();
        List<Message> messages = kieBuilder.getResults().getMessages();
        if (!messages.isEmpty()) {
            fail(messages.toString());
        }
        
        if (testRunType == RUN_TYPE.STANDARD_FROM_DRL) { 
            return ks.newKieContainer(releaseId).newKieSession();
        } else {
            InternalKieModule kieModule = (InternalKieModule) kieBuilder.getKieModule();
            File kjarFile = TestFileUtils.bytesToTempKJARFile( releaseId, kieModule.getBytes(), ".jar" );
            KieModule zipKieModule = new CanonicalKieModule( releaseId, getDefaultKieModuleModel( ks ), kjarFile );
            repo.addKieModule( zipKieModule );
            
            KieContainer kieContainer = ks.newKieContainer( releaseId );
            KieSession kieSession = kieContainer.newKieSession();
            
            return kieSession;
        }
    }
    private KieModuleModel getDefaultKieModuleModel(KieServices ks) {
        KieModuleModel kproj = ks.newKieModuleModel();
        KieBaseModel kieBaseModel1 = kproj.newKieBaseModel( "kbase" ).setDefault( true );
        KieSessionModel ksession1 = kieBaseModel1.newKieSessionModel( "ksession" ).setDefault( true );
        return kproj;

    }

    @Test
    public void testInlineCast() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $o : Object( this#Person.name == \"Mark\" )\n" +
                "then\n" +
                "  System.out.println(\"Found: \" + $o);\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( "Mark" );
        ksession.insert(new Person("Mark", 37));
        ksession.insert(new Person("Mario", 40));
        ksession.fireAllRules();
    }

    @Test
    public void testNullSafeDereferncing() {
        String str =
                "import " + Person.class.getCanonicalName() + ";" +
                "rule R when\n" +
                "  $o : Person( name.length == 4 )\n" +
                "then\n" +
                "  System.out.println(\"Found: \" + $o);\n" +
                "end";

        KieSession ksession = getKieSession( str );

        ksession.insert( "Mark" );
        ksession.insert(new Person("Mark", 37));
//        ksession.insert(new Person(null, 40));
        ksession.fireAllRules();
    }
}
