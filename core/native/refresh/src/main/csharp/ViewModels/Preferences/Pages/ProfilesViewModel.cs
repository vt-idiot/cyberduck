using ch.cyberduck.core.profiles;
using Ch.Cyberduck.Core.Refresh.Models;
using Ch.Cyberduck.Core.Refresh.Services;
using DynamicData;
using DynamicData.Binding;
using org.apache.logging.log4j;
using ReactiveUI;
using ReactiveUI.Fody.Helpers;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Reactive;
using System.Reactive.Linq;
using System.Reactive.Subjects;
using System.Threading.Tasks;

namespace Ch.Cyberduck.Core.Refresh.ViewModels.Preferences.Pages
{
    using ch.cyberduck.core;

    public class ProfilesViewModel : ReactiveObject
    {
        private static Logger log = LogManager.getLogger(typeof(ProfilesViewModel).FullName);

        public ProfilesViewModel(ProtocolFactory protocols, ProfileListObserver profileListObserver, ProfilesProvider provider)
        {
            LoadProfiles = ReactiveCommand.Create(() => provider.Profiles);

            var profiles = LoadProfiles.DistinctUntilChanged().Do(_ => Busy = true).Switch()
                .Filter(x => x.getProfile().isPresent())
                .Filter(this.WhenAnyValue(v => v.FilterText)
                    .Throttle(TimeSpan.FromMilliseconds(500))
                    .DistinctUntilChanged()
                    .Select(v => (Func<ProfileDescription, bool>)new SearchProfilePredicate(v).test))
                .Transform(x => new ProfileViewModel(x))
                .AsObservableList();

            profiles.Connect()
                .Sort(SortExpressionComparer<ProfileViewModel>.Ascending(x => x.Profile))
                .ObserveOnDispatcher()
                .Bind(Profiles)
                .Subscribe(_ => Busy = false);

            profiles.Connect()
                .WhenPropertyChanged(v => v.Installed, false)
                .Subscribe(p =>
                {
                    if (p.Value)
                    {
                        var file = p.Sender.ProfileDescription.getFile();
                        if (file.isPresent())
                        {
                            var local = (Local)file.get();
                            protocols.register(local);
                        }
                    }
                    else
                    {
                        protocols.unregister(p.Sender.Profile);
                    }
                    profileListObserver.RaiseProfilesChanged();
                });
        }

        [Reactive]
        public bool Busy { get; set; }

        [Reactive]
        public string FilterText { get; set; }

        public ReactiveCommand<Unit, IObservableList<ProfileDescription>> LoadProfiles { get; }

        public BindingList<ProfileViewModel> Profiles { get; } = new();
    }
}
