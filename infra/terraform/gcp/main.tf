terraform {
  required_version = ">= 1.5"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }

  # 로컬 state (1인 프로젝트)
  backend "local" {}
}

provider "google" {
  project = var.project_id
  region  = var.region
}
