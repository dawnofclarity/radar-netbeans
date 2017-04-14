package qubexplorer.ui.summary;

import java.util.List;
import qubexplorer.server.ui.CustomServerIssuesAction;
import org.openide.windows.WindowManager;
import qubexplorer.ClassifierSummary;
import qubexplorer.ClassifierType;
import qubexplorer.ConfigurationFactory;
import qubexplorer.IssuesContainer;
import qubexplorer.NoSuchProjectException;
import qubexplorer.ResourceKey;
import qubexplorer.SonarQubeProjectConfiguration;
import qubexplorer.filter.IssueFilter;
import qubexplorer.server.SonarQube;
import qubexplorer.ui.UserCredentialsRepository;
import qubexplorer.ui.ProjectContext;
import qubexplorer.server.ui.ServerConnectionDialog;
import qubexplorer.ui.SonarIssuesTopComponent;
import qubexplorer.ui.issues.IssueLocation;
import qubexplorer.ui.task.Task;
import qubexplorer.ui.task.TaskExecutor;

/**
 *
 * @author Victor
 */
public class SummaryTask extends Task<ClassifierSummary> {

    private final IssuesContainer issuesContainer;
    private final List<IssueFilter> filters;
    private final ClassifierType classifierType;

    public SummaryTask(IssuesContainer issuesContainer, ProjectContext projectContext, ClassifierType classifierType, List<IssueFilter> filters) {
        super(projectContext, issuesContainer instanceof SonarQube ? ((SonarQube) issuesContainer).getServerUrl() : null);
        this.issuesContainer = issuesContainer;
        this.classifierType=classifierType;
        this.filters = filters;
    }

    @Override
    protected void init() {
        SonarIssuesTopComponent sonarTopComponent = (SonarIssuesTopComponent) WindowManager.getDefault().findTopComponent("SonarIssuesTopComponent");
        sonarTopComponent.resetState(classifierType);
    }

    @Override
    public ClassifierSummary execute() {
        return issuesContainer.getSummary(classifierType, getUserCredentials(), getProjectContext().getConfiguration().getKey(), filters);
    }

    @Override
    protected void success(ClassifierSummary summary) {
        SonarIssuesTopComponent sonarTopComponent = (SonarIssuesTopComponent) WindowManager.getDefault().findTopComponent("SonarIssuesTopComponent");
        sonarTopComponent.setProjectContext(getProjectContext());
        sonarTopComponent.setProjectKeyChecker(new SimpleChecker());
        sonarTopComponent.setIssuesContainer(issuesContainer);
        sonarTopComponent.open();
        sonarTopComponent.requestVisible();
        sonarTopComponent.showSummary(classifierType, summary);
    }

    @Override
    protected void fail(Throwable cause) {
        if (cause instanceof NoSuchProjectException) {
            assert issuesContainer instanceof SonarQube;
            String serverUrl = ((SonarQube) issuesContainer).getServerUrl();
            if (getUserCredentials() != null) {
                UserCredentialsRepository.getInstance().saveUserCredentials(serverUrl, null, getUserCredentials());
            }
            ServerConnectionDialog connectionDialog = new ServerConnectionDialog(WindowManager.getDefault().getMainWindow(), true);
            connectionDialog.setSelectedUrl(serverUrl);
            connectionDialog.loadProjectKeys();
            if (connectionDialog.showDialog() == ServerConnectionDialog.Option.ACCEPT) {
                SonarQubeProjectConfiguration fixed = connectionDialog.getSelectedProject();
                SonarQubeProjectConfiguration real = ConfigurationFactory.createDefaultConfiguration(getProjectContext().getProject());
                ProjectContext newProjectContext = new ProjectContext(getProjectContext().getProject(), new CustomServerIssuesAction.FixedKey(fixed, real));
                TaskExecutor.execute(new SummaryTask(new SonarQube(connectionDialog.getSelectedUrl()), newProjectContext, classifierType, filters));
            }
        } else {
            super.fail(cause);
        }
    }

    private static class SimpleChecker implements IssueLocation.ProjectKeyChecker {

        @Override
        public boolean equals(SonarQubeProjectConfiguration configuration, ResourceKey projectKeyIssue, boolean isSubmodule) {
            return configuration.getKey().equals(projectKeyIssue);
        }

    }

}
