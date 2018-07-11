# System-wide .bashrc file for interactive bash(1) shells.

# To enable the settings / commands in this file for login shells as well,
# this file has to be sourced in /etc/profile.

# If not running interactively, don't do anything
if [ -n "$PS1" ]; then
    # check the window size after each command and, if necessary,
    # update the values of LINES and COLUMNS.
    shopt -s checkwinsize

    # set variable identifying the chroot you work in (used in the prompt below)
    if [ -z "${debian_chroot:-}" ] && [ -r /etc/debian_chroot ]; then
        debian_chroot=$(cat /etc/debian_chroot)
    fi

    # set a fancy prompt (non-color, overwrite the one in /etc/profile)
    PS1='${debian_chroot:+($debian_chroot)}\u@\h:\w\$ '

    # enable bash completion in interactive shells
    if ! shopt -oq posix; then
      if [ -f /usr/share/bash-completion/bash_completion ]; then
        . /usr/share/bash-completion/bash_completion
      elif [ -f /etc/bash_completion ]; then
        . /etc/bash_completion
      fi
    fi

    # if the command-not-found package is installed, use it
    if [ -x /usr/lib/command-not-found -o -x /usr/share/command-not-found/command-not-found ]; then
            function command_not_found_handle {
                    # check because c-n-f could've been removed in the meantime
                    if [ -x /usr/lib/command-not-found ]; then
                       /usr/lib/command-not-found -- "$1"
                       return $?
                    elif [ -x /usr/share/command-not-found/command-not-found ]; then
                       /usr/share/command-not-found/command-not-found -- "$1"
                       return $?
                    else
                       printf "%s: command not found\n" "$1" >&2
                       return 127
                    fi
            }
    fi
fi

export MANTA_ENV_WORKING=1

if [ -n "${MANTA_PRIVATE_KEY}" ] && [ ! -f $HOME/.ssh/id_rsa ]; then
    echo "${MANTA_PRIVATE_KEY}" | base64 -d > $HOME/.ssh/id_rsa
    chmod og-rwx $HOME/.ssh/id_rsa
    unset MANTA_PRIVATE_KEY
fi

if [ -n "${MANTA_PRIVATE_KEY}" ] && [ -f $HOME/.ssh/id_rsa ]; then
    unset MANTA_PRIVATE_KEY
fi

if [ -z "${MANTA_USER}" ]; then
    >&2 echo "MANTA_USER must be set in order for Manta adaptor to work"
    export MANTA_ENV_WORKING=0
fi

if [ -z "${MANTA_URL}" ]; then
    >&2 echo "MANTA_URL is not set. Defaulting to: https://us-east.manta.joyent.com:443"
    export MANTA_URL=https://us-east.manta.joyent.com:443
fi

if [ $MANTA_ENV_WORKING -ne 1 ]; then
    >&2 echo "Manta environment is not setup correctly"
fi

export MANTA_KEY_ID=$(ssh-keygen -l -f $HOME/.ssh/id_rsa | awk '{print $2}')
