import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

/**
 * Created by ivan on 11/15/14.
 */
public class SpringEntrypoint extends Entrypoint {
    private final DefaultEntrypoint defaultEntryPoint;

    public SpringEntrypoint(IMethod iMethod, IClassHierarchy cha) {
        super(iMethod);
        this.defaultEntryPoint = new DefaultEntrypoint(iMethod,cha);

    }

    @Override
    public TypeReference[] getParameterTypes(int i) {
        return defaultEntryPoint.getParameterTypes(i);
    }

    @Override
    public int getNumberOfParameters() {
        return defaultEntryPoint.getNumberOfParameters();
    }
}
