package org.eclipse.pde.targetplatformexporter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository;
import org.eclipse.equinox.internal.p2.core.helpers.LogHelper;
import org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository;
import org.eclipse.equinox.internal.p2.repository.Transport;
import org.eclipse.equinox.internal.p2.repository.helpers.RepositoryHelper;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.internal.repository.mirroring.Mirroring;
import org.eclipse.equinox.p2.internal.repository.tools.RepositoryDescriptor;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.IRepository;
import org.eclipse.equinox.p2.repository.IRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.ServiceReference;

@SuppressWarnings("restriction")
public class P2MirrorTool {
	private SubMonitor monitor;
	private IProvisioningAgent agent;
	private CompositeMetadataRepository compositeMetadataRepository;
	private CompositeArtifactRepository compositeArtifactRepository;
	protected IArtifactRepository destinationArtifactRepository = null;
	protected IMetadataRepository destinationMetadataRepository = null;
	protected List<URI> artifactReposToRemove = new ArrayList<URI>();
	protected List<URI> metadataReposToRemove = new ArrayList<URI>();
	private String destFolder;
	private IArtifactRepository destArtifactRepository;
	private IMetadataRepository destMetadataRepository;
	private Set<IInstallableUnit> installableUnitSet;
	private Set<URI> repoURIs;

	public P2MirrorTool(Set<URI> repoURIs, Set<IInstallableUnit> installableUnitSet, String destFolder) {
		this.repoURIs = repoURIs;
		this.installableUnitSet = installableUnitSet;
		this.destFolder = destFolder;
	}

	public MultiStatus mirror(IProgressMonitor monitor) throws MirrorException, InterruptedException {
		try {
			this.monitor = SubMonitor.convert(monitor);

			this.monitor.beginTask("Creating p2 repository", 8);

			this.monitor.subTask("Initializing agent");
			try {
				setupAgent();
			} catch (ProvisionException e) {
				LogHelper.log(e);
			}
			this.monitor.worked(1);
			
			if (repoURIs.size()> 1) {
				this.monitor.subTask("Init source repository");
			} else {
				this.monitor.subTask("Init " + repoURIs.size() +" source repositories");
			}
			initCompositeRepositories(repoURIs);
			this.monitor.worked(1);
			
			this.monitor.subTask("Init destination repository: " + destFolder);
			initDestinationRepository(); // worked(2)
			
			this.monitor.subTask("Mirroring " +installableUnitSet.size() + " artifacts");
			MultiStatus status = mirrorArtifacts();
			
			if (monitor.isCanceled()) {
				throw new InterruptedException();
			}
			
			this.monitor.subTask("Mirror metadata");
			mirrorMetadata();
			
			this.monitor.worked(3);

			return status;
		} finally {
			finalizeRepositories();
			if (monitor != null) {
				monitor.done();
			}
		}
	}
	
	private void initCompositeRepositories(Set<URI> repoURIs) {
		compositeArtifactRepository = CompositeArtifactRepository.createMemoryComposite(agent);
		for (URI uri : repoURIs) {
			compositeArtifactRepository.addChild(uri);
		}
		
		compositeMetadataRepository = CompositeMetadataRepository.createMemoryComposite(agent);
		for (URI uri : repoURIs) {
			compositeMetadataRepository.addChild(uri);
		}
	}

	private void initDestinationRepository() throws MirrorException {
		IArtifactRepositoryManager artifactRepositoryManager = getArtifactRepositoryManager();
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		
		URI uri;
		try {
			uri = URIUtil.fromString(destFolder);
		} catch (URISyntaxException e) {
			throw new MirrorException("Invalid destination repository folder: '" + destFolder + "'");
		}
		
		RepositoryDescriptor destRepositoryDescriptor = new RepositoryDescriptor();

		//destRepoDesc.setFormat(sourceURI);
		destRepositoryDescriptor.setAppend(true);
		destRepositoryDescriptor.setCompressed(true);
		destRepositoryDescriptor.setName("Target");
		destRepositoryDescriptor.setAtomic("true");
		destRepositoryDescriptor.setLocation(RepositoryHelper.localRepoURIHelper(uri));
		
		try {
			destArtifactRepository = addRepository(artifactRepositoryManager, destRepositoryDescriptor.getRepoLocation(), IRepositoryManager.REPOSITORY_HINT_MODIFIABLE);
		} catch (ProvisionException exception) {
			try {
				destArtifactRepository = artifactRepositoryManager.createRepository(destRepositoryDescriptor.getRepoLocation(), destRepositoryDescriptor.getName(), IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
			} catch (ProvisionException exception2) {
				throw new MirrorException("Error loading/initializing artifact repository in '" + destFolder + "': " + exception2.toString(), exception2);
			}
		}

		try {
			destMetadataRepository = addRepository(metadataRepositoryManager, destRepositoryDescriptor.getRepoLocation(), IRepositoryManager.REPOSITORY_HINT_MODIFIABLE);
		} catch (ProvisionException exception) {
			try {
				destMetadataRepository = metadataRepositoryManager.createRepository(destRepositoryDescriptor.getRepoLocation(), destRepositoryDescriptor.getName(), IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, null);
			} catch (ProvisionException exception2) {
				throw new MirrorException("Error loading/initializing metadata repository in '" + destFolder + "': " + exception2.toString(), exception2);
			}
		}
		
		if (!destArtifactRepository.isModifiable() || !destMetadataRepository.isModifiable()) {
			throw new RuntimeException("Not midifiable");
		}

		destMetadataRepository.setProperty(IRepository.PROP_COMPRESSED, "true");
		destArtifactRepository.setProperty(IRepository.PROP_COMPRESSED, "true");
	}

	private void setupAgent() throws ProvisionException {
		//note if we ever wanted these applications to act on a different agent than
		//the currently running system we would need to set it here
		ServiceReference<IProvisioningAgent> agentRef = Activator.getBundleContext().getServiceReference(IProvisioningAgent.class);
		if (agentRef != null) {
			agent = Activator.getBundleContext().getService(agentRef);
			if (agent != null)
				return;
		}
		//there is no agent around so we need to create one
		ServiceReference<IProvisioningAgentProvider> providerRef = Activator.getBundleContext().getServiceReference(IProvisioningAgentProvider.class);
		if (providerRef == null)
			throw new RuntimeException("No provisioning agent provider is available"); //$NON-NLS-1$
		IProvisioningAgentProvider provider = Activator.getBundleContext().getService(providerRef);
		if (provider == null)
			throw new RuntimeException("No provisioning agent provider is available"); //$NON-NLS-1$
		//obtain agent for currently running system
		agent = provider.createAgent(null);
		Activator.getBundleContext().ungetService(providerRef);
	}

	protected IArtifactRepositoryManager getArtifactRepositoryManager() {
		return (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
	}

	protected IMetadataRepositoryManager getMetadataRepositoryManager() {
		return (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
	}
	
	//Helper to add a repository. It takes care of adding the repos to the deletion list and loading it 
	protected IMetadataRepository addRepository(IMetadataRepositoryManager manager, URI location, int flags) throws ProvisionException {
		if (!manager.contains(location))
			metadataReposToRemove.add(location);
		return manager.loadRepository(location, flags, monitor.newChild(1));
	}

	//Helper to add a repository. It takes care of adding the repos to the deletion list and loading it
	protected IArtifactRepository addRepository(IArtifactRepositoryManager manager, URI location, int flags) throws ProvisionException {
		if (!manager.contains(location))
			artifactReposToRemove.add(location);
		return manager.loadRepository(location, flags, monitor.newChild(1));
	}

	private void finalizeRepositories() {
		IArtifactRepositoryManager artifactRepositoryManager = getArtifactRepositoryManager();
		for (URI uri : artifactReposToRemove)
			artifactRepositoryManager.removeRepository(uri);
		IMetadataRepositoryManager metadataRepositoryManager = getMetadataRepositoryManager();
		for (URI uri : metadataReposToRemove)
			metadataRepositoryManager.removeRepository(uri);
		
		metadataReposToRemove = null;
		artifactReposToRemove = null;
		destinationArtifactRepository = null;
		destinationMetadataRepository = null;
	}


	protected Mirroring getMirroring() {

		List<IArtifactKey> keys = new ArrayList<IArtifactKey>();
		for (IInstallableUnit iu : installableUnitSet) {
			keys.addAll(iu.getArtifacts());
		}
		
		Mirroring mirror = new Mirroring(compositeArtifactRepository, destArtifactRepository, false);
		mirror.setValidate(false);
		mirror.setTransport((Transport) agent.getService(Transport.SERVICE_NAME));
		mirror.setIncludePacked(false);

		// If IUs have been specified then only they should be mirrored, otherwise mirror everything.
		if (keys.size() > 0)
			mirror.setArtifactKeys(keys.toArray(new IArtifactKey[keys.size()]));

		return mirror;
	}
	
	private MultiStatus mirrorArtifacts() throws MirrorException {
		return getMirroring().run(true, true);
	}

	private void mirrorMetadata() {
		destMetadataRepository.addInstallableUnits(installableUnitSet);
	}
}
