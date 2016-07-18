package soicodegen.wizards;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class CreateSoiCommandWizard extends Wizard implements INewWizard {
	public static final String TEMPLATES_PATH = Platform.getInstallLocation().getURL().getPath() + "plugins/soicodegen/templates";
	private CreateSoiCommandWizardPage page;
	private ISelection selection;

	public CreateSoiCommandWizard() {
		super();
		setNeedsProgressMonitor(true);
	}
	
	public void addPages() {
		page = new CreateSoiCommandWizardPage(selection);
		addPage(page);
	}

	/**
	 * This method is called when 'Finish' button is pressed in
	 * the wizard. We will create an operation and run it
	 * using wizard as execution context.
	 */
	public boolean performFinish() {
		
		final String commandName = page.getCommandName();
		final String commandVersion = page.getCommandVersion();
		final String commandModule = page.getCommandModule();
		final String projectPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toString()+page.getContainerName();
		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					doFinish(commandName, commandVersion, commandModule, projectPath, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		};
		try {
			getContainer().run(true, false, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			Throwable realException = e.getTargetException();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}
		return true;
	}

	
	private void doFinish(String commandName, String commandVersion, String moduleName, String projectPath, IProgressMonitor monitor)
	throws CoreException
	{
		try{
			addSOIEntry(commandName, commandVersion, projectPath);
	        String commdnImplementationName= getCommandImplementationName(commandName);
	        addRegistryEntry(commdnImplementationName, projectPath);
	        File cdfDir = new File(projectPath+"/src/resource/cms/cdf/"+commandName.replace('.', '_'));
	        if(!cdfDir.exists())
	        {
	            cdfDir.mkdir();
	        }
	        createCommandDefinitionFile(commandName, commandVersion, projectPath);
	        createCommandImplementationFile(commdnImplementationName, commandName, commandVersion, projectPath);
	        insertModule(moduleName, commandName, projectPath);
	        IResource dfile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(projectPath));
	        dfile.refreshLocal(IResource.DEPTH_INFINITE, null);
		}catch(Exception ex)
		{
			throwCoreException("cannot create command, error:" + ex.getMessage());
		} 
		
	}
	

	private void throwCoreException(String message) throws CoreException {
		IStatus status =
			new Status(IStatus.ERROR, "soicodegen", IStatus.OK, message, null);
		throw new CoreException(status);
	}

	/**
	 * We will accept the selection in the workbench to see if
	 * we can initialize from it.
	 * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
	}
	
	
	/**
     * adds an entry for the command in the SOI definition file EXI_1.xml
     * @param cmdName
     * @param CmdVersion
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws TransformerException
     */
    private void addSOIEntry(String cmdName, String CmdVersion, String projectPath) throws ParserConfigurationException, SAXException, IOException, TransformerException
    {
        String soiFile = projectPath + "/src/resource/cms/soi/EXI_1.xml";
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(soiFile);
        Node soi = doc.getElementsByTagName("SOI").item(0);
        Element command = doc.createElement("COMMAND");
        command.setAttribute("cmdName", cmdName);
        command.setAttribute("cmdVersion", CmdVersion);
        command.setAttribute("deprecated", "no");
        soi.appendChild(command);
        FileOutputStream fos = new FileOutputStream(new File(soiFile));
        DOMImplementation domImpl = doc.getImplementation();
        DocumentType doctype = domImpl.createDocumentType("SOI", "SOIDefinition.dtd", "SOIDefinition.dtd"); 
        LSSerializer serializer = ((DOMImplementationLS) domImpl).createLSSerializer();
        serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        LSOutput lso = ((DOMImplementationLS) domImpl).createLSOutput();
        lso.setByteStream(fos);
        serializer.write(doc,lso);
         
    }
    
    /**
     * adds an entry in the Registry file for the command implementation class
     * @param cmdImplementation
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws XPathExpressionException
     * @throws TransformerException
     */
    private void addRegistryEntry(String cmdImplementation, String projectPath) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException, TransformerException
    {
        String registryFile = projectPath + "/src/resource/cms/plugin/EXI_Registry.xml";
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(registryFile);
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("//Node[@name=\"ServiceLayerAdapterNames\"]");
        NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        Node node = nl.item(0);
        Element command = doc.createElement("KeyValue");
        command.setAttribute("key", "com.lhs.extendedinterface.common.service."+cmdImplementation);
        command.setAttribute("type", "String");
        command.setAttribute("value", "");
        command.setAttribute("comment", "null");
        node.appendChild(command);
        FileOutputStream fos = new FileOutputStream(new File(registryFile));
        DOMImplementation domImpl = doc.getImplementation();
        DocumentType doctype = domImpl.createDocumentType("ComponentRegistry", "ComponentRegistry.dtd", "ComponentRegistry.dtd");     
        LSSerializer serializer = ((DOMImplementationLS) domImpl).createLSSerializer();
        serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        LSOutput lso = ((DOMImplementationLS) domImpl).createLSOutput();
        lso.setByteStream(fos);
        serializer.write(doc,lso); 
    }
    
    /**
     * creates command definition file from the template
     * @param commandName
     * @throws IOException
     * @throws TemplateException
     */
    private void createCommandDefinitionFile(String commandName, String commandVersion, String projectPath) throws IOException, TemplateException
    {
        Configuration cfg = new Configuration();
        cfg.setDirectoryForTemplateLoading(new File(TEMPLATES_PATH));
        Template template = cfg.getTemplate("CMD_1_0.ftl");
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("commandName", commandName);
        data.put("commandVersion", commandVersion);
        // File output
        Writer file = new FileWriter (new File(projectPath + "/src/resource/cms/cdf/"+commandName.replace('.', '_')+"/" + commandVersion+".xml"));
        template.process(data, file);
        file.flush();
        file.close();
    }
    
    /**
     * creates command implementation class from the template
     * @param cmdImplementation
     * @param commandName
     * @throws IOException
     * @throws TemplateException
     */
    private void createCommandImplementationFile(String cmdImplementation, String commandName, String commandVersion, String projectPath) throws IOException, TemplateException
    {
        Configuration cfg = new Configuration();
        cfg.setDirectoryForTemplateLoading(new File(TEMPLATES_PATH));
        Template template = cfg.getTemplate("CommandImplemantation.ftl");
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("commandImplementationName", cmdImplementation);
        data.put("commandName", commandName);
        data.put("commandVersion", commandVersion);
        // File output
        Writer file = new FileWriter (new File(projectPath + "/src/java/com/lhs/extendedinterface/common/service/" + cmdImplementation+".java"));
        template.process(data, file);
        file.flush();
        file.close(); 
    }
    
    /**
     * formulates the command implementation class name from the command name
     * @param commandName
     * @return
     */
    private String getCommandImplementationName(String commandName)
    {
      StringBuffer strBuf = new StringBuffer();
      String[] words = commandName.split("_|\\.");
      for(String word: words)
      {
          strBuf.append(convertToCamel(word)); 
      }
      return strBuf.toString();
    }
    
    /**
     * converts the word to camel casing
     * </p>
     * for example:
     * CONTRACT to Contract
     * @param word
     * @return
     */
    private String convertToCamel(String word)
    {
        return new String(word.substring(0,1).toUpperCase()+word.substring(1).toLowerCase());      
    }
    
    /**
     * inserts an entry for the command in the module definition script
     * @param moduleName
     * @param commandName
     * @throws IOException
     */
    private void insertModule(String moduleName, String commandName, String projectPath) throws IOException
    {
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(projectPath + "/sql/cms_addon_modules.sql", true)));
        out.println();
        out.println("insert into modules (modulename, modulenumber, description, entdate, perm_code, soi_command, max_user_rights)");
        out.println("             values('"+moduleName+"', -1, '"+commandName+"', sysdate, '"+commandName+"', 'X', 'W');");
        out.println();
        out.println("insert into modules_hierarchy(parent, child, grpperm) values('CMSCMDS', '"+moduleName+"', 2147483647);");
        out.println();
        out.flush();
        out.close(); 
    }
}