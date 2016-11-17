package qubexplorer.ui.summary;

import org.openide.windows.WindowManager;
import qubexplorer.ConfigurationFactory;
import qubexplorer.IssuesContainer;
import qubexplorer.NoSuchProjectException;
import qubexplorer.ResourceKey;
import qubexplorer.SonarQubeProjectConfiguration;
import qubexplorer.Summary;
import qubexplorer.filter.IssueFilter;
import qubexplorer.server.SonarQube;
import qubexplorer.ui.AuthenticationRepository;
import qubexplorer.ui.ProjectChooser;
import qubexplorer.ui.ProjectContext;
import qubexplorer.ui.SonarIssuesTopComponent;
import qubexplorer.ui.issues.IssueLocation;
import qubexplorer.ui.task.Task;
import qubexplorer.ui.task.TaskExecutor;

/**
 *
 * @author Victor
 */
public class SummaryTask extends Task<Summary>{
    private final IssuesContainer issuesContainer;
    private final IssueFilter[] filters;

    public SummaryTask(IssuesContainer issuesContainer, ProjectContext projectContext, IssueFilter[] filters) {
        super(projectContext, issuesContainer instanceof SonarQube? ((SonarQube)issuesContainer).getServerUrl(): null);
        this.issuesContainer = issuesContainer;
        this.filters = filters;
    }

    @Override
    protected void init() {
        SonarIssuesTopComponent sonarTopComponent = (SonarIssuesTopComponent) WindowManager.getDefault().findTopComponent("SonarIssuesTopComponent");
        sonarTopComponent.resetState();
    }
    
    @Override
    public Summary execute() {
        return issuesContainer.getSummary(getUserCredentials(), getProjectContext().getConfiguration().getKey(), filters);
    }
    
    @Override
    protected void success(Summary summary) {
        SonarIssuesTopComponent sonarTopComponent = (SonarIssuesTopComponent) WindowManager.getDefault().findTopComponent("SonarIssuesTopComponent");
        sonarTopComponent.setProjectContext(getProjectContext());
        sonarTopComponent.setProjectKeyChecker(new SimpleChecker());
        sonarTopComponent.setIssuesContainer(issuesContainer);
        sonarTopComponent.open();
        sonarTopComponent.requestVisible();
        sonarTopComponent.showSummary(summary);
    }

    @Override
    protected void fail(Throwable cause) {
        if(cause instanceof NoSuchProjectException) {
            assert issuesContainer instanceof SonarQube;
            SonarQube sonarQube=(SonarQube) issuesContainer;
            if(getUserCredentials()!= null) {
                AuthenticationRepository.getInstance().saveAuthentication(sonarQube.getServerUrl(), null, getUserCredentials());
            }
            ProjectChooser chooser=new ProjectChooser(WindowManager.getDefault().getMainWindow(), true);
            chooser.setSelectedUrl(sonarQube.getServerUrl());
            chooser.setServerUrlEnabled(false);
            chooser.loadProjectKeys();
            if(chooser.showDialog() == ProjectChooser.Option.ACCEPT) {
                SonarQubeProjectConfiguration fixed = chooser.getSelectedProject();
                SonarQubeProjectConfiguration real = ConfigurationFactory.createDefaultConfiguration(getProjectContext().getProject());
                ProjectContext newProjectContext = new ProjectContext(getProjectContext().getProject(), new CustomServerIssuesAction.FixedKey(fixed, real));
                //final SonarQube sonarQube = new SonarQube(chooser.getSelectedUrl());
                TaskExecutor.execute(new SummaryTask(issuesContainer, newProjectContext, filters));
            }
        }else{
            super.fail(cause);
        }
    }
    
    private static class SimpleChecker implements IssueLocation.ProjectKeyChecker{

        @Override
        public boolean equals(SonarQubeProjectConfiguration configuration, ResourceKey projectKeyIssue, boolean isSubmodule) {
            return configuration.getKey().equals(projectKeyIssue);
        }
        
    }
    
}
