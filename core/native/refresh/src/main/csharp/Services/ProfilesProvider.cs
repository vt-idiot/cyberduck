using ch.cyberduck.core;
using ch.cyberduck.core.profiles;
using Ch.Cyberduck.Core.Refresh.Models;
using DynamicData;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Ch.Cyberduck.Core.Refresh.Services
{
    public class ProfilesProvider
    {
        private readonly IObservable<IChangeSet<ProfileDescription>> cache;
        private readonly Dictionary<ProfileDescription, DescribedProfile> installed = new();
        private IObservableList<ProfileDescription> profiles;

        public ProfilesProvider(ProtocolFactory protocols, ProfileListObserver profileListObserver, Controller controller)
        {
            cache = ObservableChangeSet.Create<ProfileDescription>(async list =>
            {
                TaskCompletionSource<IEnumerable<ProfileDescription>> result = new();
                var worker = new InternalProfilesSynchronizeWorker(protocols, result);
                var action = new ProfilesWorkerBackgroundAction(controller, worker);
                controller.background(action);
                list.EditDiff(await result.Task);
            });
        }

        public IObservableList<ProfileDescription> Profiles => profiles ??= (cache.AsObservableList());

        private class InternalProfilesSynchronizeWorker : ProfilesSynchronizeWorker
        {
            private readonly TaskCompletionSource<IEnumerable<ProfileDescription>> completionSource;

            public InternalProfilesSynchronizeWorker(ProtocolFactory protocolFactory, TaskCompletionSource<IEnumerable<ProfileDescription>> completionSource) : base(protocolFactory, ProfilesFinder.Visitor.Prefetch)
            {
                this.completionSource = completionSource;
            }

            public override void cleanup(object result) => completionSource.SetResult(Utils.ConvertFromJavaList<ProfileDescription>((java.util.Collection)result));
        }
    }
}
