package com.marklogic.gradle.task

import com.marklogic.appdeployer.AppDeployer
import com.marklogic.appdeployer.command.Command
import com.marklogic.appdeployer.command.modules.LoadModulesCommand
import com.marklogic.appdeployer.command.schemas.LoadSchemasCommand
import com.marklogic.appdeployer.command.security.DeployRolesCommand
import com.marklogic.appdeployer.impl.SimpleAppDeployer
import com.marklogic.mgmt.util.ObjectMapperFactory
import com.marklogic.rest.util.PreviewInterceptor
import org.gradle.api.tasks.TaskAction

/**
 * Extends DeployAppTask and applies an instance of PreviewInterceptor to the RestTemplate objects associated with
 * the ManageClient created by MarkLogicPlugin. Because of this extension, a user can use the "ignore" property
 * supported by the parent class to ignore any commands that cause issues with doing a preview.
 */
class PreviewDeployTask extends DeployAppTask {

	@TaskAction
	void deployApp() {
		modifyAppConfigBeforePreview()
		modifyAppDeployerBeforePreview()

		PreviewInterceptor interceptor = configurePreviewInterceptor()

		super.deployApp()

		println "\nPREVIEW OF DEPLOYMENT:\n"
		println ObjectMapperFactory.getObjectMapper().writeValueAsString(interceptor.getResults())
	}

	void modifyAppConfigBeforePreview() {
		// Disable loading of any modules
		getAppConfig().setModulePaths(new ArrayList<String>())

		// Disable loading of schemas from the default path
		// Database-specific schema paths are handled by removing instances of LoadSchemasCommand
		getAppConfig().setSchemasPath(null)
	}

	void modifyAppDeployerBeforePreview() {
		AppDeployer deployer = getAppDeployer()

		if (deployer instanceof SimpleAppDeployer) {
			SimpleAppDeployer simpleAppDeployer = (SimpleAppDeployer) deployer

			List<Command> newCommands = new ArrayList<>()
			for (Command c : simpleAppDeployer.getCommands()) {
				if (c instanceof LoadSchemasCommand || c instanceof LoadModulesCommand) {
					// Don't include these; no need to load schemas or modules during a preview
				}
				// Loading roles in two phases breaks the preview feature, so it's disabled
				else if (c instanceof DeployRolesCommand) {
					DeployRolesCommand deployRolesCommand = (DeployRolesCommand) c
					deployRolesCommand.setDeployRolesInTwoPhases(false)
					newCommands.add(c)
				} else {
					newCommands.add(c)
				}
			}
			simpleAppDeployer.setCommands(newCommands)
		}
	}

	PreviewInterceptor configurePreviewInterceptor() {
		PreviewInterceptor interceptor = new PreviewInterceptor(getManageClient())
		getManageClient().getRestTemplate().getInterceptors().add(interceptor)
		getManageClient().getRestTemplate().setErrorHandler(interceptor)
		if (getManageClient().getRestTemplate() != getManageClient().getSecurityUserRestTemplate()) {
			getManageClient().getSecurityUserRestTemplate().getInterceptors().add(interceptor)
			getManageClient().getSecurityUserRestTemplate().setErrorHandler(interceptor)
		}
		return interceptor
	}
}
