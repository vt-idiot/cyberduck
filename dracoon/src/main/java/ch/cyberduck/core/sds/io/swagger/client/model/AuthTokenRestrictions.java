/*
 * DRACOON API
 * REST Web Services for DRACOON<br><br>This page provides an overview of all available and documented DRACOON APIs, which are grouped by tags.<br>Each tag provides a collection of APIs that are intended for a specific area of the DRACOON.<br><br><a title='Developer Information' href='https://developer.dracoon.com'>Developer Information</a>&emsp;&emsp;<a title='Get SDKs on GitHub' href='https://github.com/dracoon'>Get SDKs on GitHub</a><br><br><a title='Terms of service' href='https://www.dracoon.com/terms/general-terms-and-conditions/'>Terms of service</a>
 *
 * OpenAPI spec version: 4.30.0-beta.4
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */

package ch.cyberduck.core.sds.io.swagger.client.model;

import java.util.Objects;
import java.util.Arrays;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * Auth token restrictions
 */
@Schema(description = "Auth token restrictions")
@javax.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.JavaClientCodegen", date = "2021-08-16T11:28:10.116221+02:00[Europe/Zurich]")
public class AuthTokenRestrictions {
  @JsonProperty("restrictionEnabled")
  private Boolean restrictionEnabled = null;

  @JsonProperty("accessTokenValidity")
  private Integer accessTokenValidity = null;

  @JsonProperty("refreshTokenValidity")
  private Integer refreshTokenValidity = null;

  public AuthTokenRestrictions restrictionEnabled(Boolean restrictionEnabled) {
    this.restrictionEnabled = restrictionEnabled;
    return this;
  }

   /**
   * &amp;#128640; Since v4.13.0  Defines if OAuth token restrictions are enabled
   * @return restrictionEnabled
  **/
  @Schema(description = "&#128640; Since v4.13.0  Defines if OAuth token restrictions are enabled")
  public Boolean isRestrictionEnabled() {
    return restrictionEnabled;
  }

  public void setRestrictionEnabled(Boolean restrictionEnabled) {
    this.restrictionEnabled = restrictionEnabled;
  }

  public AuthTokenRestrictions accessTokenValidity(Integer accessTokenValidity) {
    this.accessTokenValidity = accessTokenValidity;
    return this;
  }

   /**
   * &amp;#128640; Since v4.13.0  Restricted OAuth access token validity (in seconds)
   * @return accessTokenValidity
  **/
  @Schema(description = "&#128640; Since v4.13.0  Restricted OAuth access token validity (in seconds)")
  public Integer getAccessTokenValidity() {
    return accessTokenValidity;
  }

  public void setAccessTokenValidity(Integer accessTokenValidity) {
    this.accessTokenValidity = accessTokenValidity;
  }

  public AuthTokenRestrictions refreshTokenValidity(Integer refreshTokenValidity) {
    this.refreshTokenValidity = refreshTokenValidity;
    return this;
  }

   /**
   * &amp;#128640; Since v4.13.0  Restricted OAuth refresh token validity (in seconds)
   * @return refreshTokenValidity
  **/
  @Schema(description = "&#128640; Since v4.13.0  Restricted OAuth refresh token validity (in seconds)")
  public Integer getRefreshTokenValidity() {
    return refreshTokenValidity;
  }

  public void setRefreshTokenValidity(Integer refreshTokenValidity) {
    this.refreshTokenValidity = refreshTokenValidity;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AuthTokenRestrictions authTokenRestrictions = (AuthTokenRestrictions) o;
    return Objects.equals(this.restrictionEnabled, authTokenRestrictions.restrictionEnabled) &&
        Objects.equals(this.accessTokenValidity, authTokenRestrictions.accessTokenValidity) &&
        Objects.equals(this.refreshTokenValidity, authTokenRestrictions.refreshTokenValidity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(restrictionEnabled, accessTokenValidity, refreshTokenValidity);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AuthTokenRestrictions {\n");
    
    sb.append("    restrictionEnabled: ").append(toIndentedString(restrictionEnabled)).append("\n");
    sb.append("    accessTokenValidity: ").append(toIndentedString(accessTokenValidity)).append("\n");
    sb.append("    refreshTokenValidity: ").append(toIndentedString(refreshTokenValidity)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}
