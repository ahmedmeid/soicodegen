package com.lhs.extendedinterface.common.service;

import org.eclipse.persistence.sessions.UnitOfWork;

import com.lhs.ccb.common.soi.SVLObject;
import com.lhs.ccb.func.ect.ComponentException;
import com.lhs.ccb.sfw.application.CommandVersionNotSupportedException;
import com.lhs.ccb.sfw.application.DomainServiceAdapter;
import com.lhs.ccb.sfw.application.ImplementationRegistry;
import com.lhs.ccb.sfw.application.InconsistentRegistryException;
import com.lhs.ccb.sfw.application.ServiceContext;
import com.lhs.ccb.sfw.domain.TransactionContext;

public class ${commandImplementationName} implements DomainServiceAdapter
{
    public static final String COMMAND_NAME    = "${commandName}";
    public static final String COMMAND_VERSION = "${commandVersion}";
    public static ${commandImplementationName} _instance;
    /**
     * 
     */
    private ${commandImplementationName}()
    {
        super();
        initialize(); 
    }
    
    /**
     * 
     */
    public void initialize()
    {
        try
        {
            ImplementationRegistry.registerImplementation(COMMAND_NAME, COMMAND_VERSION, this);
        }
        catch (InconsistentRegistryException e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void execute(ServiceContext pServiceContext, String pCmd,String pCmdVer, SVLObject pSVLInput, SVLObject pSVLOutput) 
            throws CommandVersionNotSupportedException,
            ComponentException
    
    {  
      UnitOfWork unitOfWork = TransactionContext.getCurrent().getUnitOfWork();
          
    }
    
    
    /*
     * 
     */
    public static ${commandImplementationName} getInstance()
    {
        if (_instance == null)
        {
            synchronized (com.lhs.extendedinterface.common.service.${commandImplementationName}.class)
            {
                if(_instance == null)
                {
                    _instance = new ${commandImplementationName}();
                }
            }
        }
        return _instance;
    }
}

