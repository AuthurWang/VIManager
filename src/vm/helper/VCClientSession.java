package vm.helper;

/*
 * Created by huxia on 2017/3/13.
 * Completed By Huxiao
 */

import com.vmware.vim25.*;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import javax.net.ssl.*;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.SOAPFaultException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @功能描述 客户程序的登陆、登出和认证
 */
public class VCClientSession {
    // 记录所有连接的数目，只有当登录的所有方法全部结束时才断开连接
    // private static int logCounter = 0;

    private static final String url = "https://10.251.0.20:8000/sdk";
    private static final String username = "administrator@vsphere.local";
    private static final String password = "@Vmware123";
    private static Logger logger = Logger.getLogger(VCClientSession.class);
    private static final String SVC_INST_NAME = "ServiceInstance";

    private final ManagedObjectReference SVC_INST_REF = new ManagedObjectReference();
    private VimService vimService;
    private VimPortType vimPort;
    private ServiceContent serviceContent;
    private boolean isConnected = false;
    private ManagedObjectReference perfManager;
    private ManagedObjectReference propCollectorRef;

    public VimService getVimService() {
        if (!isConnected) {
            try {
                Connect();
            } catch (RuntimeFaultFaultMsg | NoSuchAlgorithmException | KeyManagementException | InvalidLocaleFaultMsg | InvalidLoginFaultMsg runtimeFaultFaultMsg) {
                runtimeFaultFaultMsg.printStackTrace();
            }
        }
        return vimService;
    }

    VimPortType getVimPort() {
        if (!isConnected) {
            try {
                Connect();
            } catch (RuntimeFaultFaultMsg | NoSuchAlgorithmException | KeyManagementException | InvalidLocaleFaultMsg | InvalidLoginFaultMsg runtimeFaultFaultMsg) {
                runtimeFaultFaultMsg.printStackTrace();
            }
        }
        return vimPort;
    }

    ServiceContent getServiceContent() {
        if (!isConnected) {
            try {
                Connect();
            } catch (RuntimeFaultFaultMsg | NoSuchAlgorithmException | KeyManagementException | InvalidLocaleFaultMsg | InvalidLoginFaultMsg runtimeFaultFaultMsg) {
                runtimeFaultFaultMsg.printStackTrace();
            }
        }
        return serviceContent;
    }

    public ManagedObjectReference getPerfManager() {
        return perfManager;
    }

    public ManagedObjectReference getPropCollectorRef() {
        return propCollectorRef;
    }

    private void trustAllHttpsCertificates() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[1];
        TrustManager tm = new TrustAllTrustManager();
        trustAllCerts[0] = tm;
        SSLContext sc = SSLContext.getInstance("SSL");
        SSLSessionContext sslsc = sc.getServerSessionContext();
        sslsc.setSessionTimeout(0);
        sc.init(null, trustAllCerts, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    /**
     * @功能描述 连接认证
     */
    void Connect() throws RuntimeFaultFaultMsg, InvalidLoginFaultMsg, InvalidLocaleFaultMsg, KeyManagementException, NoSuchAlgorithmException {
        //if(logCounter <= 0) {
        //    logCounter = 0;
        //}
        //logCounter++;
        if (!isConnected) {
            HostnameVerifier hv = (s, sslSession) -> true;
            trustAllHttpsCertificates();

            HttpsURLConnection.setDefaultHostnameVerifier(hv);

            SVC_INST_REF.setType(SVC_INST_NAME);
            SVC_INST_REF.setValue(SVC_INST_NAME);

            vimService = new VimService();
            vimPort = vimService.getVimPort();
            Map<String, Object> ctxt = ((BindingProvider) vimPort).getRequestContext();

            ctxt.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
            ctxt.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);

            serviceContent = vimPort.retrieveServiceContent(SVC_INST_REF);
            vimPort.login(serviceContent.getSessionManager(), username, password, null);
            isConnected = true;

            perfManager = serviceContent.getPerfManager();
            propCollectorRef = serviceContent.getPropertyCollector();

            logger.info("Connected successfully.\n" + serviceContent.getAbout().getFullName() + "\nServer type is " + serviceContent.getAbout().getApiType());
        }
    }

    /**
     * @功能描述 断开连接
     */
    void Disconnect() throws RuntimeFaultFaultMsg {
        //logCounter--;
        if (isConnected) {
            vimPort.logout(serviceContent.getSessionManager());
            isConnected = false;
            //logCounter = 0;

            logger.info("Disconnected successfully.");
        }
    }

    /**
     * @功能描述 打印错误信息
     */
    private void printSoapFaultException(SOAPFaultException sfe) {
        logger.error("Soap fault: ");
        if (sfe.getFault().hasDetail()) {
            logger.error(sfe.getFault().getDetail().getFirstChild().getLocalName());
        }
        if (sfe.getFault().getFaultString() != null) {
            logger.error("Message: " + sfe.getFault().getFaultString());
        }
    }

    Object[] WaitForValues(ManagedObjectReference objmor, String[] filterProps, String[] endWaitProps, Object[][] expectedVals) throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg, InvalidCollectorVersionFaultMsg {
        ManagedObjectReference filterSpecRef = null;

        // version string is initially null
        String version = "";
        Object[] endVals = new Object[endWaitProps.length];
        Object[] filterVals = new Object[filterProps.length];
        String stateVal = null;

        PropertyFilterSpec spec = propertyFilterSpec(objmor, filterProps);

        filterSpecRef = vimPort.createFilter(serviceContent.getPropertyCollector(), spec, true);

        boolean reached = false;

        UpdateSet updateset = null;
        List<PropertyFilterUpdate> filtupary = null;
        List<ObjectUpdate> objupary = null;
        List<PropertyChange> propchgary = null;
        while (!reached) {
            updateset = vimPort.waitForUpdatesEx(serviceContent.getPropertyCollector(), version, new WaitOptions());
            if (updateset == null || updateset.getFilterSet() == null) {
                continue;
            }
            version = updateset.getVersion();

            // Make this code more general purpose when PropCol changes later.
            filtupary = updateset.getFilterSet();

            for (PropertyFilterUpdate filtup : filtupary) {
                objupary = filtup.getObjectSet();
                for (ObjectUpdate objup : objupary) {
                    // TODO: Handle all "kind"s of updates.
                    if (objup.getKind() == ObjectUpdateKind.MODIFY
                            || objup.getKind() == ObjectUpdateKind.ENTER
                            || objup.getKind() == ObjectUpdateKind.LEAVE) {
                        propchgary = objup.getChangeSet();
                        for (PropertyChange propchg : propchgary) {
                            updateValues(endWaitProps, endVals, propchg);
                            updateValues(filterProps, filterVals, propchg);
                        }
                    }
                }
            }

            Object expctdval = null;
            // Check if the expected values have been reached and exit the loop
            // if done.
            // Also exit the WaitForUpdates loop if this is the case.
            for (int chgi = 0; chgi < endVals.length && !reached; chgi++) {
                for (int vali = 0; vali < expectedVals[chgi].length && !reached; vali++) {
                    expctdval = expectedVals[chgi][vali];
                    if (endVals[chgi] == null) {
                        // Do Nothing
                    } else if (endVals[chgi].toString().contains("val: null")) {
                        // Due to some issue in JAX-WS De-serialization getting the infomation from
                        // the nodes
                        Element stateElement = (Element) endVals[chgi];
                        if (stateElement != null && stateElement.getFirstChild() != null) {
                            stateVal = stateElement.getFirstChild().getTextContent();
                            reached = expctdval.toString().equalsIgnoreCase(stateVal) || reached;
                        }
                    } else {
                        expctdval = expectedVals[chgi][vali];
                        reached = expctdval.equals(endVals[chgi]) || reached;
                        stateVal = "filtervals";
                    }
                }
            }
        }
        Object[] retVal = null;
        // Destroy the filter when we are done.
        try {
            vimPort.destroyPropertyFilter(filterSpecRef);
        } catch (RuntimeFaultFaultMsg e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (stateVal != null) {
            if (stateVal.equalsIgnoreCase("ready")) {
                retVal = new Object[]{HttpNfcLeaseState.READY};
            }
            if (stateVal.equalsIgnoreCase("error")) {
                retVal = new Object[]{HttpNfcLeaseState.ERROR};
            }
            if (stateVal.equals("filtervals")) {
                retVal = filterVals;
            }
        } else {
            retVal = new Object[]{HttpNfcLeaseState.ERROR};
        }
        return retVal;
    }

    private static PropertyFilterSpec propertyFilterSpec(ManagedObjectReference objmor, String[] filterProps) {
        PropertyFilterSpec spec = new PropertyFilterSpec();
        ObjectSpec oSpec = new ObjectSpec();
        oSpec.setObj(objmor);
        oSpec.setSkip(Boolean.FALSE);
        spec.getObjectSet().add(oSpec);

        PropertySpec pSpec = new PropertySpec();
        pSpec.getPathSet().addAll(Arrays.asList(filterProps));
        pSpec.setType(objmor.getType());
        spec.getPropSet().add(pSpec);
        return spec;
    }

    private static void updateValues(String[] props, Object[] vals, PropertyChange propchg) {
        for (int findi = 0; findi < props.length; findi++) {
            if (propchg.getName().lastIndexOf(props[findi]) >= 0) {
                if (propchg.getOp() == PropertyChangeOp.REMOVE) {
                    vals[findi] = "";
                } else {
                    vals[findi] = propchg.getVal();
                }
            }
        }
    }

    private static class TrustAllTrustManager implements javax.net.ssl.TrustManager, javax.net.ssl.X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public boolean isServerTrusted(X509Certificate[] certs) {
            return true;
        }

        public boolean isClientTrusted(java.security.cert.X509Certificate[] certs) {
            return true;
        }
    }
}
