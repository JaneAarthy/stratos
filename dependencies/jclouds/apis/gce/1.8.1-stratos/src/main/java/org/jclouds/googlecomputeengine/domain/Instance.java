/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.googlecomputeengine.domain;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getLast;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.util.Date;
import java.util.Set;

import org.jclouds.javax.annotation.Nullable;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

/**
 * Represents a virtual machine.
 *
 * @see <a href="https://developers.google.com/compute/docs/reference/v1/instances"/>
 */
@Beta
public class Instance extends Resource {

   public enum Status {
      PROVISIONING,
      STAGING,
      RUNNING,
      STOPPING,
      STOPPED,
      TERMINATED
   }

   protected final Tags tags;
   protected final URI machineType;
   protected final Status status;
   protected final Optional<String> statusMessage;
   protected final URI zone;
   protected final Set<NetworkInterface> networkInterfaces;
   protected final Set<AttachedDisk> disks;
   protected final Metadata metadata;
   protected final Set<ServiceAccount> serviceAccounts;

   protected Instance(String id, Date creationTimestamp, URI selfLink, String name, String description,
                      Tags tags, URI machineType, Status status, String statusMessage,
                      URI zone, Set<NetworkInterface> networkInterfaces, Set<AttachedDisk> disks,
                      Metadata metadata, Set<ServiceAccount> serviceAccounts) {
      super(Kind.INSTANCE, id, creationTimestamp, selfLink, name, description);
      this.tags = checkNotNull(tags, "tags");
      this.machineType = checkNotNull(machineType, "machineType of %s", name);
      this.status = checkNotNull(status, "status");
      this.statusMessage = fromNullable(statusMessage);
      this.zone = checkNotNull(zone, "zone of %s", name);
      this.networkInterfaces = networkInterfaces == null ? ImmutableSet.<NetworkInterface>of() : networkInterfaces;
      this.disks = disks == null ? ImmutableSet.<AttachedDisk>of() : disks;
      this.metadata = checkNotNull(metadata, "metadata");
      this.serviceAccounts = serviceAccounts == null ? ImmutableSet.<ServiceAccount>of() : serviceAccounts;
   }

   /**
    * Used to identify valid sources or targets for network firewalls. Provided by the client when the instance is
    * created. Each tag must be unique, must be 1-63 characters long, and comply with RFC1035.
    *
    * @return an optional set of items applied to this instance.
    */
   public Tags getTags() {
      return tags;
   }

   /**
    * @return URL of the machine type resource describing which machine type to use to host the instance.
    */
   public URI getMachineType() {
      return machineType;
   }

   /**
    * @return Instance status
    */
   public Status getStatus() {
      return status;
   }

   /**
    * @return an optional, human-readable explanation of the status.
    */
   @Nullable
   public Optional<String> getStatusMessage() {
      return statusMessage;
   }

   /**
    * @return URL of the zone resource describing where this instance should be hosted; provided by the client when
    *         the instance is created.
    */
   public URI getZone() {
      return zone;
   }

   /**
    * @return set of NetworkInterfaces
    * @see NetworkInterface
    */
   public Set<NetworkInterface> getNetworkInterfaces() {
      return networkInterfaces;
   }

   /**
    * @return array of disks associated with this instance. Persistent disks must be created before
    *         you can assign them.
    * @see org.jclouds.googlecomputeengine.domain.Instance.AttachedDisk
    */
   public Set<AttachedDisk> getDisks() {
      return disks;
   }

   /**
    * @return metadata for this instance
    */
   public Metadata getMetadata() {
      return metadata;
   }

   /**
    * @return list of service accounts each with specified scopes.
    * @see ServiceAccount
    */
   public Set<ServiceAccount> getServiceAccounts() {
      return serviceAccounts;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || getClass() != obj.getClass()) return false;
      Instance that = Instance.class.cast(obj);
      return equal(this.kind, that.kind)
              && equal(this.name, that.name)
              && equal(this.zone, that.zone);
   }

   /**
    * {@inheritDoc}
    */
   protected Objects.ToStringHelper string() {
      return super.string()
              .omitNullValues()
              .add("items", tags)
              .add("machineType", machineType)
              .add("status", status)
              .add("statusMessage", statusMessage.orNull())
              .add("zone", zone)
              .add("networkInterfaces", networkInterfaces)
              .add("disks", disks)
              .add("metadata", metadata)
              .add("serviceAccounts", serviceAccounts);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String toString() {
      return string().toString();
   }

   public static Builder builder() {
      return new Builder();
   }

   public Builder toBuilder() {
      return new Builder().fromInstance(this);
   }

   public static final class Builder extends Resource.Builder<Builder> {

      private Tags tags;
      private URI machineType;
      private Status status;
      private String statusMessage;
      private URI zone;
      private Metadata metadata;
      private ImmutableSet.Builder<NetworkInterface> networkInterfaces = ImmutableSet.builder();
      private ImmutableSet.Builder<AttachedDisk> disks = ImmutableSet.builder();
      private ImmutableSet.Builder<ServiceAccount> serviceAccounts = ImmutableSet.builder();


      /**
       * @see Instance#getTags()
       */
      public Builder tags(Tags tags) {
         this.tags = tags;
         return this;
      }

      /**
       * @see Instance#getMachineType()
       */
      public Builder machineType(URI machineType) {
         this.machineType = machineType;
         return this;
      }

      /**
       * @see Instance#getStatus()
       */
      public Builder status(Status status) {
         this.status = status;
         return this;
      }

      /**
       * @see Instance#getStatusMessage()
       */
      public Builder statusMessage(String statusMessage) {
         this.statusMessage = statusMessage;
         return this;
      }

      /**
       * @see Instance#getZone()
       */
      public Builder zone(URI zone) {
         this.zone = zone;
         return this;
      }

      /**
       * @see Instance#getNetworkInterfaces()
       */
      public Builder addNetworkInterface(NetworkInterface networkInterface) {
         this.networkInterfaces.add(networkInterface);
         return this;
      }

      /**
       * @see Instance#getNetworkInterfaces()
       */
      public Builder networkInterfaces(Set<NetworkInterface> networkInterfaces) {
         this.networkInterfaces.addAll(networkInterfaces);
         return this;
      }

      /**
       * @see Instance#getDisks()
       */
      public Builder addDisk(AttachedDisk disk) {
         this.disks.add(disk);
         return this;
      }

      /**
       * @see Instance#getDisks()
       */
      public Builder disks(Set<AttachedDisk> disks) {
         this.disks.addAll(disks);
         return this;
      }

      /**
       * @see Instance#getMetadata()
       */
      public Builder metadata(Metadata metadata) {
         this.metadata = metadata;
         return this;
      }

      /**
       * @see Instance#getServiceAccounts()
       */
      public Builder addServiceAccount(ServiceAccount serviceAccount) {
         this.serviceAccounts.add(serviceAccount);
         return this;
      }

      /**
       * @see Instance#getServiceAccounts()
       */
      public Builder serviceAccounts(Set<ServiceAccount> serviceAccounts) {
         this.serviceAccounts.addAll(serviceAccounts);
         return this;
      }


      @Override
      protected Builder self() {
         return this;
      }

      public Instance build() {
         return new Instance(super.id, super.creationTimestamp, super.selfLink, super.name,
                 super.description, tags, machineType, status, statusMessage, zone,
                 networkInterfaces.build(), disks.build(), metadata, serviceAccounts.build());
      }

      public Builder fromInstance(Instance in) {
         return super.fromResource(in)
                 .tags(in.getTags())
                 .machineType(in.getMachineType())
                 .status(in.getStatus())
                 .statusMessage(in.getStatusMessage().orNull())
                 .zone(in.getZone())
                 .networkInterfaces(in.getNetworkInterfaces())
                 .disks(in.getDisks())
                 .metadata(in.getMetadata())
                 .serviceAccounts(in.getServiceAccounts());
      }
   }




   /**
    * Tags for an instance, with their fingerprint.
    */
   public static class Tags {
      private final String fingerprint;
      private final Set<String> items;

      @ConstructorProperties({"fingerprint", "items"})
      public Tags(String fingerprint, @Nullable Set<String> items) {
         this.fingerprint = checkNotNull(fingerprint);
         this.items = items == null ? ImmutableSet.<String>of() : items;
      }

      /**
       * Used to identify valid sources or targets for network firewalls. Provided by the client when the instance is
       * created. Each tag must be unique, must be 1-63 characters long, and comply with RFC1035.
       *
       * @return an optional set of items applied to this instance.
       */
      public Set<String> getItems() {
         return items;
      }

      /**
       * Gets the fingerprint for the items - needed for updating them.
       *
       * @return the fingerprint string for the items.
       */
      public String getFingerprint() {
         return fingerprint;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int hashCode() {
         return Objects.hashCode(fingerprint, items);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (obj == null || getClass() != obj.getClass()) return false;
         Tags that = Tags.class.cast(obj);
         return equal(this.items, that.items)
                 && equal(this.fingerprint, that.fingerprint);
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this)
                 .add("items", items)
                 .add("fingerprint", fingerprint);
      }

      public static Builder builder() {
         return new Builder();
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
         return string().toString();
      }

      public static final class Builder {

         private ImmutableSet.Builder<String> items = ImmutableSet.builder();
         private String fingerprint;

         /**
          * @see Tags#getItems()
          */
         public Builder addItem(String item) {
            this.items.add(item);
            return this;
         }

         /**
          * @see Tags#getItems()
          */
         public Builder items(Set<String> items) {
            this.items.addAll(items);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.Tags#getFingerprint()
          */
         public Builder fingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
            return this;
         }

         public Tags build() {
            return new Tags(this.fingerprint, this.items.build());
         }

         public Builder fromTags(Tags in) {
            return this.fingerprint(in.getFingerprint())
                    .items(in.getItems());
         }
      }
   }

   /**
    * A disk attached to an Instance.
    *
    * @see <a href="https://developers.google.com/compute/docs/reference/v1/instances"/>
    */
   public static class AttachedDisk {

      private final int index;

      public AttachedDisk(Integer index) {
         this.index = checkNotNull(index, "index");
      }

      public boolean isPersistent() {
         return false;
      }

      /**
       * @return a zero-based index to assign to this disk, where 0 is reserved for the boot disk.
       */
      public int getIndex() {
         return index;
      }


      /**
       * {@inheritDoc}
       */
      @Override
      public int hashCode() {
         return Objects.hashCode(index);
      }


      /**
       * {@inheritDoc}
       */
      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (obj == null || getClass() != obj.getClass()) return false;
         AttachedDisk that = AttachedDisk.class.cast(obj);
         return equal(this.index, that.index);
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this).add("index", index);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
         return string().toString();
      }

      public static AttachedDisk ephemeralDiskAtIndex(Integer index) {
         return new AttachedDisk(index);
      }
   }

   public static class PersistentAttachedDisk extends AttachedDisk {
      public enum Mode {
         READ_WRITE,
         READ_ONLY;
      }

      @ConstructorProperties({"mode", "source", "deviceName", "index", "deleteOnTerminate",
              "boot"})
      public PersistentAttachedDisk(Mode mode, URI source, String deviceName, Integer index,
                                    boolean deleteOnTerminate, boolean boot) {
         super(index);
         this.mode = checkNotNull(mode, "mode");
         this.source = checkNotNull(source, "source");
         this.deviceName = fromNullable(deviceName);
         this.deleteOnTerminate = deleteOnTerminate;
         this.boot = boot;
      }

      private final Mode mode;
      private final URI source;
      private final boolean deleteOnTerminate;
      private final Optional<String> deviceName;
      private final boolean boot;

      @Override
      public boolean isPersistent() {
         return true;
      }

      /**
       * @return the mode in which to attach this disk, either READ_WRITE or READ_ONLY.
       */
      public Mode getMode() {
         return mode;
      }

      /**
       * @return the URL of the persistent disk resource.
       */
      public URI getSource() {
         return source;
      }

      /**
       * @return the Name of the persistent disk resource
       */
      public String getSourceDiskName() {
         return getLast(Splitter.on("/").split(source.toString()), null);
      }

      /**
       * @return Must be unique within the instance when specified. This represents a unique
       *         device name that is reflected into the /dev/ tree of a Linux operating system running within the
       *         instance. If not specified, a default will be chosen by the system.
       */
      public Optional<String> getDeviceName() {
         return deviceName;
      }


      /**
       * @return If true, delete the disk and all its data when the associated instance is deleted.
       */
      public boolean isDeleteOnTerminate() {
         return deleteOnTerminate;
      }

      /**
       * @return If true, this is the boot disk for this instance.
       */
      public boolean isBoot() {
         return boot;
      }

      public static Builder builder() {
         return new Builder();
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this).add("boot", boot);
      }


      public static final class Builder {

         private Mode mode;
         private URI source;
         private String deviceName;
         private Integer index;
         private boolean deleteOnTerminate;
         private boolean boot;

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.PersistentAttachedDisk#getMode()
          */
         public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.PersistentAttachedDisk#getSource()
          */
         public Builder source(URI source) {
            this.source = source;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.PersistentAttachedDisk#getDeviceName()
          */
         public Builder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.AttachedDisk#getIndex()
          */
         public Builder index(Integer index) {
            this.index = index;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.PersistentAttachedDisk#isDeleteOnTerminate()
          */
         public Builder deleteOnTerminate(Boolean deleteOnTerminate) {
            this.deleteOnTerminate = deleteOnTerminate;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.PersistentAttachedDisk#isBoot()
          */
         public Builder boot(Boolean boot) {
            this.boot = boot;
            return this;
         }

         public PersistentAttachedDisk build() {
            return new PersistentAttachedDisk(this.mode, this.source, this.deviceName, this.index,
                    this.deleteOnTerminate, this.boot);
         }

         public Builder fromPersistentAttachedDisk(PersistentAttachedDisk in) {
            return this.mode(in.getMode())
                    .source(in.getSource())
                    .deviceName(in.getDeviceName().orNull())
                    .index(in.getIndex())
                    .deleteOnTerminate(in.isDeleteOnTerminate())
                    .boot(in.isBoot());
         }
      }
   }

   /**
    * A network interface for an Instance.
    *
    * @see <a href="https://developers.google.com/compute/docs/reference/v1/instances"/>
    */
   public static final class NetworkInterface {

      private final String name;
      private final URI network;
      private final Optional<String> networkIP;
      private final Set<AccessConfig> accessConfigs;

      @ConstructorProperties({
              "name", "network", "networkIP", "accessConfigs"
      })
      private NetworkInterface(String name, URI network, String networkIP,
                               Set<AccessConfig> accessConfigs) {
         this.name = checkNotNull(name, "name");
         this.network = checkNotNull(network, "network");
         this.networkIP = fromNullable(networkIP);
         this.accessConfigs = accessConfigs == null ? ImmutableSet.<AccessConfig>of() : accessConfigs;
      }

      /**
       * @return the name of the network interface
       */
      public String getName() {
         return name;
      }

      /**
       * @return URL of the network resource attached to this interface.
       */
      public URI getNetwork() {
         return network;
      }

      /**
       * @return An IPV4 internal network address to assign to this instance.
       */
      public Optional<String> getNetworkIP() {
         return networkIP;
      }

      /**
       * @return array of access configurations for this interface.
       */
      public Set<AccessConfig> getAccessConfigs() {
         return accessConfigs;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int hashCode() {
         return Objects.hashCode(name, network, networkIP, accessConfigs);
      }


      /**
       * {@inheritDoc}
       */
      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (obj == null || getClass() != obj.getClass()) return false;
         NetworkInterface that = NetworkInterface.class.cast(obj);
         return equal(this.name, that.name)
                 && equal(this.network, that.network);
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this)
                 .add("name", name)
                 .add("network", network).add("networkIP", networkIP).add("accessConfigs",
                         accessConfigs);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
         return string().toString();
      }

      public static Builder builder() {
         return new Builder();
      }

      public Builder toBuilder() {
         return builder().fromNetworkInterface(this);
      }

      public static class Builder {

         private String name;
         private URI network;
         private String networkIP;
         private ImmutableSet.Builder<AccessConfig> accessConfigs = ImmutableSet.builder();

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface#getName()
          */
         public Builder name(String name) {
            this.name = name;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface#getNetwork()
          */
         public Builder network(URI network) {
            this.network = network;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface#getNetworkIP()
          */
         public Builder networkIP(String networkIP) {
            this.networkIP = networkIP;
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface#getAccessConfigs()
          */
         public Builder addAccessConfig(AccessConfig accessConfig) {
            this.accessConfigs.add(accessConfig);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface#getAccessConfigs()
          */
         public Builder accessConfigs(Set<AccessConfig> accessConfigs) {
            this.accessConfigs = ImmutableSet.builder();
            this.accessConfigs.addAll(accessConfigs);
            return this;
         }

         public NetworkInterface build() {
            return new NetworkInterface(this.name, this.network, this.networkIP, this.accessConfigs.build());
         }

         public Builder fromNetworkInterface(NetworkInterface in) {
            return this.network(in.getNetwork())
                    .networkIP(in.getNetworkIP().orNull())
                    .accessConfigs(in.getAccessConfigs());
         }
      }

      /**
       * Access configuration to an instance's network.
       * <p/>
       * This specifies how this interface is configured to interact with other network services,
       * such as connecting to the internet. Currently, ONE_TO_ONE_NAT is the only access config supported.
       */
      public static final class AccessConfig {

         public enum Type {
            ONE_TO_ONE_NAT
         }

         private Optional<String> name;
         private Type type;
         private Optional<String> natIP;

         @ConstructorProperties({
                 "name", "type", "natIP"
         })
         private AccessConfig(String name, Type type, String natIP) {
            this.name = fromNullable(name);
            this.type = checkNotNull(type, "type");
            this.natIP = fromNullable(natIP);
         }

         /**
          * @return name of this access configuration.
          */
         public Optional<String> getName() {
            return name;
         }

         /**
          * @return type of configuration. Must be set to ONE_TO_ONE_NAT. This configures port-for-port NAT to the
          *         internet.
          */
         public Type getType() {
            return type;
         }

         /**
          * @return an external IP address associated with this instance, if there is one.
          */
         @Nullable
         public Optional<String> getNatIP() {
            return natIP;
         }

         /**
          * {@inheritDoc}
          */
         @Override
         public int hashCode() {
            return Objects.hashCode(name, type, natIP);
         }

         /**
          * {@inheritDoc}
          */
         @Override
         public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            AccessConfig that = AccessConfig.class.cast(obj);
            return equal(this.name, that.name)
                    && equal(this.type, that.type)
                    && equal(this.natIP, that.natIP);
         }

         /**
          * {@inheritDoc}
          */
         protected Objects.ToStringHelper string() {
            return toStringHelper(this)
                    .add("name", name).add("type", type).add("natIP", natIP);
         }

         /**
          * {@inheritDoc}
          */
         @Override
         public String toString() {
            return string().toString();
         }

         public static Builder builder() {
            return new Builder();
         }

         public Builder toBuilder() {
            return builder().fromAccessConfig(this);
         }

         public static class Builder {

            private String name;
            private Type type;
            private String natIP;

            /**
             * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface.AccessConfig#getName()
             */
            public Builder name(String name) {
               this.name = name;
               return this;
            }

            /**
             * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface.AccessConfig#getType()
             */
            public Builder type(Type type) {
               this.type = type;
               return this;
            }

            /**
             * @see org.jclouds.googlecomputeengine.domain.Instance.NetworkInterface.AccessConfig#getNatIP()
             */
            public Builder natIP(String natIP) {
               this.natIP = natIP;
               return this;
            }

            public AccessConfig build() {
               return new AccessConfig(name, type, natIP);
            }

            public Builder fromAccessConfig(AccessConfig in) {
               return this.name(in.getName().orNull())
                       .type(in.getType())
                       .natIP(in.getNatIP().orNull());
            }
         }
      }
   }

   /**
    * The output of an instance's serial port;
    *
    * @see <a href="https://developers.google.com/compute/docs/reference/v1/instances/serialPort"/>
    */
   public static final class SerialPortOutput {

      private final Optional<String> selfLink;
      private final String contents;

      @ConstructorProperties({
              "selfLink", "contents"
      })
      public SerialPortOutput(String selfLink, String contents) {
         this.selfLink = fromNullable(selfLink);
         this.contents = checkNotNull(contents, "contents");
      }

      /**
       * @return unique identifier for the resource; defined by the server (output only).
       */
      public Optional<String> getSelfLink() {
         return selfLink;
      }

      /**
       * @return the contents of the console output.
       */
      public String getContents() {
         return contents;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int hashCode() {
         return Objects.hashCode(selfLink, contents);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (obj == null || getClass() != obj.getClass()) return false;
         SerialPortOutput that = SerialPortOutput.class.cast(obj);
         return equal(this.selfLink, that.selfLink);
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this).add("selfLink", selfLink).add("contents", contents);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
         return string().toString();
      }

      public static Builder builder() {
         return new Builder();
      }

      public Builder toBuilder() {
         return builder().fromInstanceSerialPortOutput(this);
      }

      public static final class Builder {

         private String selfLink;
         private String contents;

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.SerialPortOutput#getSelfLink()
          */
         public Builder selfLink(String selfLink) {
            this.selfLink = checkNotNull(selfLink);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.SerialPortOutput#getContents()
          */
         public Builder contents(String contents) {
            this.contents = contents;
            return this;
         }

         public SerialPortOutput build() {
            return new SerialPortOutput(selfLink, contents);
         }

         public Builder fromInstanceSerialPortOutput(SerialPortOutput in) {
            return this.selfLink(in.getSelfLink().orNull())
                    .contents(in.getContents());
         }
      }

   }

   /**
    * A service account for which access tokens are to be made available to the instance through metadata queries.
    *
    * @see <a href="https://developers.google.com/compute/docs/reference/v1/instances"/>
    */
   public static final class ServiceAccount {

      private final String email;
      private final Set<String> scopes;

      @ConstructorProperties({
              "email", "scopes"
      })
      public ServiceAccount(String email, Set<String> scopes) {
         this.email = checkNotNull(email, "email");
         this.scopes = checkNotNull(scopes, "scopes");
      }

      /**
       * @return email address of the service account.
       */
      public String getEmail() {
         return email;
      }

      /**
       * @return the list of scopes to be made available for this service account.
       */
      public Set<String> getScopes() {
         return scopes;
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public int hashCode() {
         return Objects.hashCode(email, scopes);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public boolean equals(Object obj) {
         if (this == obj) return true;
         if (obj == null || getClass() != obj.getClass()) return false;
         ServiceAccount that = ServiceAccount.class.cast(obj);
         return equal(this.email, that.email)
                 && equal(this.scopes, that.scopes);
      }

      /**
       * {@inheritDoc}
       */
      protected Objects.ToStringHelper string() {
         return toStringHelper(this).add("email", email).add("scopes", scopes);
      }

      /**
       * {@inheritDoc}
       */
      @Override
      public String toString() {
         return string().toString();
      }

      public static Builder builder() {
         return new Builder();
      }

      public Builder toBuilder() {
         return builder().fromInstanceServiceAccount(this);
      }

      public static final class Builder {

         private String email;
         private ImmutableSet.Builder<String> scopes = ImmutableSet.builder();

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.ServiceAccount#getEmail()
          */
         public Builder email(String email) {
            this.email = checkNotNull(email);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.ServiceAccount#getScopes()
          */
         public Builder addScopes(String scopes) {
            this.scopes.add(scopes);
            return this;
         }

         /**
          * @see org.jclouds.googlecomputeengine.domain.Instance.ServiceAccount#getScopes()
          */
         public Builder scopes(Set<String> scopes) {
            this.scopes.addAll(scopes);
            return this;
         }

         public ServiceAccount build() {
            return new ServiceAccount(email, scopes.build());
         }

         public Builder fromInstanceServiceAccount(ServiceAccount in) {
            return this.email(in.getEmail()).scopes(in.getScopes());
         }
      }
   }
}
