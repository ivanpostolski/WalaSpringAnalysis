import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassSyntheticClass;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.collections.CollectionFilter;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.PDFViewUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by ivan on 11/13/14.
 */
public class WalaGraphMain {

    public static final String[] SPRING_ANNOTATIONS_CLASSES = {"/home/ivan/.m2/repository/org/springframework/spring-web/4.0.7.RELEASE/spring-web-4.0.7.RELEASE/org/springframework/web/bind/annotation"};

    public static void main(String[] args) throws Exception, ClassHierarchyException, CallGraphBuilderCancelException {
        WalaGraphMain wgm = new WalaGraphMain();
        CallGraph callGraph = wgm.buildCallGraph(args[0],args[1]);
        Graph<CGNode> pruned = GraphSlicer.prune(callGraph, new CollectionFilter<CGNode>(GraphSlicer.slice(callGraph, new ApplicationLoaderFilter())));
        DotUtil.writeDotFile(pruned, null, null, "graph.dot");
    }

    private static class ApplicationLoaderFilter implements Filter<CGNode> {

        @Override
        public boolean accepts(CGNode o) {
            if (o instanceof CGNode) {
                CGNode n = (CGNode) o;
                return n.getMethod().getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application);
            } else if (o instanceof LocalPointerKey) {
                LocalPointerKey l = (LocalPointerKey) o;
                return accepts(l.getNode());
            } else {
                return false;
            }
        }
    }

    public static CallGraph buildCallGraph(String jarfile, String exclusionsFile) throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(jarfile,new File(exclusionsFile));
        //addSpringAnnotationsToScope(scope);
        IClassHierarchy cha = ClassHierarchy.make(scope);
        Iterable<Entrypoint> e = findSpringEndpoints(scope, cha);
        AnalysisOptions o = new AnalysisOptions(scope,e);
        CallGraphBuilder builder = Util.makeZeroOneCFABuilder(o, new AnalysisCache(), cha, scope);
        CallGraph callGraph = builder.makeCallGraph(o, null);
        PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();

        //code to find points to 4 an static field
//        IClass wired = cha.lookupClass(TypeReference.find(ClassLoaderReference.Application, "Lhello/WiredController"));
//        pointerAnalysis.getPointsToSet(pointerAnalysis.getHeapModel().getPointerKeyForStaticField(wired.getAllFields().iterator().next()));

        return callGraph;
    }

    private static void addSpringAnnotationsToScope(AnalysisScope scope) {
        Iterator<File> iterator = FileUtils.iterateFiles(new File(SPRING_ANNOTATIONS_CLASSES[0]),
                new String[]{"class"}, false);
        while (iterator.hasNext()) {
            File next = iterator.next();
            try {
                scope.addClassFileToScope(ClassLoaderReference.Application,next);
            } catch (InvalidClassFileException e) {
                e.printStackTrace();
            }
        }
    }

    //custom entrypoints by spring

    public static Iterable<Entrypoint> findSpringEndpoints(AnalysisScope scope, IClassHierarchy cha) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is null");
        }
        Iterable<Entrypoint> mainEntrypoints = Util.makeMainEntrypoints(scope, cha);
        Iterable<Entrypoint> springEndpoints = findSpringEndpoints(scope.getApplicationLoader(), cha);
        return Iterables.concat(mainEntrypoints,springEndpoints);

    }

    public static Iterable<Entrypoint> findSpringEndpoints(ClassLoaderReference clr, IClassHierarchy cha) {
        if (cha == null) {
            throw new IllegalArgumentException("cha is null");
        }

        final HashSet<Entrypoint> result = HashSetFactory.make();

        for (IClass klass : cha) {
            if (klass.getClassLoader().getReference().equals(clr)) {
                //TODO: REFACTOR OOP/ Maybe with a list
                if (klass.getName().toString().startsWith("Lorg/springframework")) continue;
                for (Annotation annotation : klass.getAnnotations()) {
                    if (isSpringComponent(annotation,cha)) {
                        //check for class methods as entry points
                        //TODO: Grab only methods mapped
                        for (IMethod iMethod : klass.getAllMethods()) {
                            if (isSpringMapping(iMethod)) {
                                result.add(new SpringEntrypoint(iMethod,cha));
                            }
                        }
                    }

                }
//                IMethod m = klass.getMethod(mainRef.getSelector());
//                if (m != null) {
//                    result.add(new DefaultEntrypoint(m, cha));
//                }
            }
        }
        return new Iterable<Entrypoint>() {
            @Override
            public Iterator<Entrypoint> iterator() {
                return result.iterator();
            }
        };
    }

    private static boolean isSpringMapping(IMethod iMethod) {
        //TODO: Check for the rigth annotations
        return true;
    }

    private static boolean isSpringComponent(Annotation annotation, IClassHierarchy cha) {
        IClass annotationClass = cha.lookupClass(annotation.getType());
        if (annotationClass == null || annotationClass.getName() == null) return false;

        //check just spring annotations
        if (!annotationClass.getName().toString().startsWith("Lorg/springframework")) return false;

        if (annotationClass.getName().toString().startsWith("Lorg/springframework/stereotype/Component")) {
            return true;
        } else {
            //check the annotationClass anotations..
            for (Annotation superAnnotation : annotationClass.getAnnotations()) {
                if (isSpringComponent(superAnnotation,cha)) return true;
            }
            return false;
        }
    }
}
